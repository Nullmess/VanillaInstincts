package fr.vanillainstincts.possession;

import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import fr.vanillainstincts.config.VanillaInstinctsServerConfig;
import fr.vanillainstincts.network.PossessionActionPayload;
import fr.vanillainstincts.network.PossessionInputPayload;
import fr.vanillainstincts.network.PossessionStatePayload;
import fr.vanillainstincts.mixin.GoalSelectorDisabledFlagsAccessor;
import fr.vanillainstincts.mixin.MobGoalSelectorAccessor;
import fr.vanillainstincts.mixin.ServerPlayerGameModeAccessor;
import fr.vanillainstincts.network.VanillaInstinctsNetwork;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Effect;
import net.minecraft.potion.PotionEffect;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.dragon.phase.PhaseChargingPlayer;
import net.minecraft.entity.boss.dragon.phase.IPhase;
import net.minecraft.entity.boss.dragon.phase.PhaseStrafePlayer;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.FoodStats;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameType;
import fr.vanillainstincts.compat.LegacyBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
/** Server-authoritative spectator possession of real mob entities. */
public final class MobPossessionManager {
    private static final int MELEE_COOLDOWN_TICKS = 10;
    private static final int RANGED_COOLDOWN_TICKS = 20;
    private static final int ENDERMAN_TELEPORT_COOLDOWN_TICKS = 40;
    private static final double ENDERMAN_TELEPORT_RANGE = 32.0D;
    private static final double PLAYER_INTERACTION_RANGE = 5.0D;
    private static final int DIMENSION_TRANSFER_GRACE_TICKS = 200;
    private static final String POSSESSION_CONTROLLER_KEY =
            "vanillainstincts_possession_controller";
    private static final ThreadLocal<UUID> MANUAL_ENDERMAN_TELEPORT =
            new ThreadLocal<>();
    private static final int INPUT_MASK = PossessionInputPayload.FORWARD
            | PossessionInputPayload.BACK | PossessionInputPayload.LEFT
            | PossessionInputPayload.RIGHT | PossessionInputPayload.JUMP
            | PossessionInputPayload.DESCEND | PossessionInputPayload.SPRINT
            | PossessionInputPayload.SNEAK | PossessionInputPayload.ATTACK
            | PossessionInputPayload.USE;
    private static final Map<UUID, Session> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, UUID> BY_MOB = new HashMap<>();
    private MobPossessionManager() {
    }
    /**
     * Toggles control of the mob currently used as the vanilla spectator camera.
     * No target id or position is accepted from the client: the player must first
     * enter a mob with vanilla Spectator controls, then run /vanillainstincts control.
     */
    public static int toggleFromSpectatorCamera(EntityPlayerMP player) {
        if (player == null) return 0;
        if (BY_PLAYER.containsKey(player.getUniqueID())) {
            // /vanillainstincts control is a true toggle. Re-running it while
            // already possessing a mob releases the mob, restores the real
            // player inventory/body, returns the camera to the player and puts
            // that player back into ordinary Spectator mode.
            stop(player, StopReason.PLAYER_REQUEST);
            if (!fr.vanillainstincts.compat.Minecraft17Compat.isSpectator(player)) {
                player.setGameMode(GameType.SPECTATOR);
            }
            return 1;
        }
        if (!VanillaInstinctsServerConfig.possessionEnabled()) {
            message(player, "message.vanillainstincts.possession.disabled");
            return 0;
        }
        if (!fr.vanillainstincts.compat.Minecraft17Compat.isSpectator(player)) {
            message(player, "message.vanillainstincts.possession.spectator_only");
            return 0;
        }
        Entity camera = player.getCamera();
        if (!(camera instanceof EntityLiving) || camera == player
                || !((EntityLiving) (camera)).isEntityAlive() || fr.vanillainstincts.compat.fr.vanillainstincts.compat.Minecraft112Compat.removed(Minecraft112Compat)((EntityLiving) camera)) {
            message(player, "message.vanillainstincts.possession.camera_required");
            return 0;
        } EntityLiving mob = (EntityLiving) (camera);
        return start(player, mob) ? 1 : 0;
    }
    public static void acceptInput(EntityPlayerMP player,
                                   PossessionInputPayload payload) {
        Session session = player == null ? null : BY_PLAYER.get(player.getUniqueID());
        if (session == null || payload == null) return;
        // Never trust arbitrary client bits or rotations. The packet carries
        // intent only; movement remains fully computed on the server.
        session.flags = payload.flags() & INPUT_MASK;
        session.yaw = MathHelper.wrapDegrees(payload.yaw());
        session.pitch = MathHelper.clamp(payload.pitch(), -90.0F, 90.0F);
    }
    public static void action(EntityPlayerMP player, int action) {
        Session session = player == null ? null : BY_PLAYER.get(player.getUniqueID());
        if (session == null || !valid(player, session.mob)) return;
        if (action >= PossessionActionPayload.SELECT_SLOT_BASE
                && action < PossessionActionPayload.SELECT_SLOT_BASE + 9) {
            PossessionPlayerInventory.select(player, session.mob,
                    action - PossessionActionPayload.SELECT_SLOT_BASE);
            return;
        }
        if (action == PossessionActionPayload.DROP_ONE) {
            dropSelected(player, session, false);
            return;
        }
        if (action == PossessionActionPayload.DROP_STACK) {
            dropSelected(player, session, true);
            return;
        }
        if (action == PossessionActionPayload.SWAP_OFFHAND) {
            swapOffhand(player, session);
            return;
        }
        // PRIMARY/SECONDARY remain accepted for protocol compatibility, but
        // normal gameplay now comes from the held ATTACK/USE input bits so
        // mining, item use and repeated attacks behave like player controls.
        if (!VanillaInstinctsServerConfig.possessionAbilitiesEnabled()) return;
        if (action == PossessionActionPayload.PRIMARY) {
            primary(player, session);
        } else if (action == PossessionActionPayload.SECONDARY) {
            secondary(player, session);
        }
    }
    public static void tick(MinecraftServer server) {
        if (server == null || BY_PLAYER.isEmpty()) return;
        Iterator<Map.Entry<UUID, Session>> iterator = BY_PLAYER.entrySet()
                .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            EntityPlayerMP player = server.getConfigurationManager().getPlayer(entry.getKey());
            Session session = entry.getValue();
            EntityLiving mob = session.mob;
            if (player != null && mob != null && mob.isEntityAlive()
                    && !fr.vanillainstincts.compat.Minecraft112Compat.removed(mob) && player.getLevel() != mob.worldObj) {
                if (session.dimensionInventorySnapshot == null) {
                    session.dimensionInventorySnapshot =
                            PossessionPlayerInventory.captureTemporary(player);
                }
                session.dimensionTransferPending = true;
                session.dimensionTransferReady = true;
                session.dimensionTransferGraceTicks = DIMENSION_TRANSFER_GRACE_TICKS;
            }
            if (session.dimensionTransferPending) {
                if (player != null && session.dimensionTransferReady
                        && mob != null && mob.isEntityAlive() && !fr.vanillainstincts.compat.Minecraft112Compat.removed(mob)) {
                    completeDimensionTransfer(player, session);
                    mob = session.mob;
                } else if (session.dimensionTransferGraceTicks-- > 0) {
                    continue;
                } else {
                    if (player != null) {
                        restoreControllerInventory(player, session);
                        restoreControllerBodyState(player, session);
                    }
                    restoreMob(session);
                    if (mob != null) BY_MOB.remove(mob.getUniqueID());
                    iterator.remove();
                    if (player != null) {
                        resetCameraAndNotify(player, StopReason.MOB_REMOVED);
                    }
                    continue;
                }
            }
            if (player != null && mob != null && !mob.isEntityAlive()) {
                restoreControllerInventory(player, session);
                restoreControllerBodyState(player, session);
                restoreMob(session);
                BY_MOB.remove(mob.getUniqueID());
                iterator.remove();
                resetCameraAndNotify(player, StopReason.MOB_DIED);
                killController(player);
                continue;
            }
            if (player != null) {
                GameType currentGameType = ((ServerPlayerGameModeAccessor) player.gameMode)
                        .vanillaInstincts$getGameModeForPlayer();
                if (session.lastObservedGameType == null) {
                    session.lastObservedGameType = currentGameType;
                } else if (currentGameType != session.lastObservedGameType) {
                    GameType previousGameType = session.lastObservedGameType;
                    session.lastObservedGameType = currentGameType;
                    if (previousGameType != GameType.SPECTATOR
                            && currentGameType == GameType.SPECTATOR) {
                        restoreControllerInventory(player, session);
                        restoreControllerBodyState(player, session);
                        restoreMob(session);
                        if (mob != null) BY_MOB.remove(mob.getUniqueID());
                        iterator.remove();
                        resetCameraAndNotify(player, StopReason.RETURNED_TO_SPECTATOR);
                        continue;
                    }
                    if (currentGameType == GameType.CREATIVE) {
                        session.mob.setInvulnerable(true);
                        session.mob.setNoGravity(true);
                    } else {
                        session.mob.setInvulnerable(session.previousMobInvulnerable);
                        applyPossessionGravity(session, true);
                    }
                }
            }
            if (player == null || !valid(player, mob)) {
                if (player != null) {
                    restoreControllerInventory(player, session);
                    restoreControllerBodyState(player, session);
                }
                restoreMob(session);
                if (mob != null) BY_MOB.remove(mob.getUniqueID());
                iterator.remove();
                if (player != null) {
                    resetCameraAndNotify(player, StopReason.INVALID_STATE);
                }
                continue;
            }
            if (player.getCamera() != mob) {
                player.setCamera(mob);
            }
            applyControl(player, session);
        }
    }
    /**
     * Run after normal player ticks so FoodStats has already converted exhaustion
     * into saturation/food-level changes for this server tick.
     */
    public static void tickFood(MinecraftServer server) {
        if (server == null || BY_PLAYER.isEmpty()) return;
        for (Map.Entry<UUID, Session> entry : BY_PLAYER.entrySet()) {
            EntityPlayerMP player = server.getConfigurationManager().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || !valid(player, session.mob)) continue;
            tickPossessionFood(player, session, player.isCreative());
        }
    }
    public static boolean isPossessed(EntityLiving mob) {
        return mob != null && BY_MOB.containsKey(mob.getUniqueID());
    }
    /**
     * Vanilla Endermen may teleport from their own hurt/environment logic even
     * while GoalSelector and Brain are suppressed. During possession only the
     * explicit right-click teleport is allowed through randomTeleport().
     */
    public static boolean mayRandomTeleport(EntityEnderman enderman) {
        if (enderman == null || !isPossessed(enderman)) return true;
        UUID allowed = MANUAL_ENDERMAN_TELEPORT.get();
        return enderman.getUniqueID().equals(allowed);
    }
    /** True for the whole possession: the mob Brain never competes with input. */
    public static boolean suppressAutonomousBrain(EntityLiving mob) {
        return isPossessed(mob);
    }
    public static boolean isPossessing(EntityPlayer player, EntityLiving mob) {
        if (player == null || mob == null) return false;
        Session session = BY_PLAYER.get(player.getUniqueID());
        return session != null && session.mob == mob;
    }
    public static EntityLiving possessedBy(EntityPlayerMP player) {
        Session session = player == null ? null : BY_PLAYER.get(player.getUniqueID());
        return session == null ? null : session.mob;
    }
    /**
     * Ender pearls are launched by the hidden EntityPlayerMP proxy because item
     * use is delegated to vanilla player mechanics. Vanilla would therefore
     * teleport that hidden proxy on impact and leave the controlled mob behind.
     * Redirect the pearl destination to the possessed body instead while
     * preserving the normal pearl fall reset and damage on that body.
     */
    public static void onEnderPearlTeleport(
            EnderTeleportEvent event) {
        if (event == null || !(event.entityLiving instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.entityLiving;
        Session session = BY_PLAYER.get(player.getUniqueID());
        if (session == null || !valid(player, session.mob)) return;
        WorldServer targetLevel = player.getLevel();
        EntityLiving mob = session.mob;
        event.setCanceled(true);
        EntityLiving teleportedMob = teleportMob1193(mob, targetLevel,
                event.getTargetX(), event.getTargetY(), event.getTargetZ(),
                mob.yRot, mob.xRot);
        if (teleportedMob == null) return;
        if (teleportedMob != mob) {
            BY_MOB.remove(mob.getUniqueID());
            session.mob = teleportedMob;
            BY_MOB.put(teleportedMob.getUniqueID(), player.getUniqueID());
            mob = teleportedMob;
        }
        mob.fallDistance = 0.0F;
        if (event.getAttackDamage() > 0.0F) {
            mob.hurt(DamageSource.FALL,
                    event.getAttackDamage());
        }
        if (!mob.isEntityAlive() || fr.vanillainstincts.compat.Minecraft112Compat.removed(mob)) return;
        // Keep the invisible controller in the same dimension/position so the
        // next possession tick cannot snap the pearl teleport back. Cross-
        // dimension pearls are handled as well as ordinary same-level throws.
        if (player.getLevel() != targetLevel) {
            fr.vanillainstincts.compat.Minecraft112Compat.teleport(player, targetLevel, mob.getX(), mob.getY(), mob.getZ(),
                    mob.yRot, mob.xRot);
        }
        syncControllerBody(player, mob);
        if (player.getCamera() != mob) {
            player.setCamera(mob);
        }
    }
    /** 1.19 has no Entity#teleportTo(WorldServer, ...). */
    private static EntityLiving teleportMob1193(EntityLiving mob, WorldServer targetLevel,
                                       double x, double y, double z,
                                       float yaw, float pitch) {
        EntityLiving moved = mob;
        if (mob.worldObj != targetLevel) {
            Entity changed = mob.changeDimension(targetLevel.dimension.getType());
            if (!(changed instanceof EntityLiving)) return null; EntityLiving changedMob = (EntityLiving) (changed);
            moved = changedMob;
        }
        fr.vanillainstincts.compat.Minecraft112Compat.teleport(moved, x, y, z);
        moved.yRot = yaw;
        moved.xRot = pitch;
        return moved;
    }

    /**
     * Capture the hidden controller state before a vanilla consumable starts.
     * FoodStats deliberately remains live; health/effects belong to the possessed
     * body and are restored on the proxy after the item finishes.
     */
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof EntityPlayerMP)) return; EntityPlayerMP player = (EntityPlayerMP) (event.getEntity());
        Session session = BY_PLAYER.get(player.getUniqueID());
        if (session == null || !valid(player, session.mob)) return;
        session.consumableSnapshot = ConsumableProxySnapshot.capture(player);
    }
    /**
     * Let vanilla consume the real inventory stack and update the controller's
     * FoodStats, then replay the finished item once on the controlled EntityLivingBase
     * so potions, enchanted apples, milk and other consumable body effects apply
     * to the mob. The proxy's own health/effects are restored immediately.
     */
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof EntityPlayerMP)) return; EntityPlayerMP player = (EntityPlayerMP) (event.getEntity());
        Session session = BY_PLAYER.get(player.getUniqueID());
        if (session == null || !valid(player, session.mob)) return;
        ItemStack consumed = event.getItem();
        PossessionConsumableController.apply(consumed, session.mob);
        if (session.consumableSnapshot != null) {
            session.consumableSnapshot.restore(player);
            session.consumableSnapshot = null;
        }
        PossessionPlayerInventory.syncEquipment(player, session.mob);
        syncControllerBody(player, session.mob);
        if (player.getCamera() != session.mob) player.setCamera(session.mob);
        player.inventoryMenu.broadcastChanges();
    }
    public static void onItemUseStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof EntityPlayerMP)) return; EntityPlayerMP player = (EntityPlayerMP) (event.getEntity());
        Session session = BY_PLAYER.get(player.getUniqueID());
        if (session != null) session.consumableSnapshot = null;
    }
    /**
     * Possession starts from Spectator, but once established it survives normal
     * Survival/Creative/Adventure changes. Switching back to Spectator releases
     * the controlled mob and restores the player body.
     */
    public static void onPlayerGameModeChange(EntityPlayerMP player,
                                               GameType current,
                                               GameType next) {
        if (player == null || current == null || next == null) return;
        Session session = BY_PLAYER.get(player.getUniqueID());
        if (session == null) return;
        if (current != GameType.SPECTATOR && next == GameType.SPECTATOR) {
            stop(player, StopReason.RETURNED_TO_SPECTATOR);
            return;
        }
        // Apply the body-side Creative/Survival semantics immediately in the
        // game-mode transition event instead of waiting for the next server tick.
        if (next == GameType.CREATIVE) {
            session.mob.setInvulnerable(true);
            session.mob.setNoGravity(true);
        } else {
            session.mob.setInvulnerable(session.previousMobInvulnerable);
            applyPossessionGravity(session, true);
        }
    }
    public static void onPlayerLogin(EntityPlayerMP player) {
        if (PossessionPlayerInventory.recoverStaleBackup(player)) {
            player.displayClientMessage(new net.minecraft.util.text.TextComponentString(
                    "Vanilla Instincts: restored inventory after an interrupted possession."),
                    false);
        }
    }
    public static void onPlayerLogout(EntityPlayerMP player) {
        stop(player, StopReason.LOGOUT);
    }
    /**
     * A possessed mob leaving a level may be changing dimension rather than
     * despawning. Keep the session alive briefly and let onMobJoin rebind the
     * destination-world instance carrying the possession marker.
     */
    public static void onMobLeave(EntityLiving mob) {
        if (mob == null) return;
        UUID playerId = BY_MOB.get(mob.getUniqueID());
        if (playerId == null) return;
        Session session = BY_PLAYER.get(playerId);
        if (session == null || session.mob != mob) return;
        MinecraftServer server = mob.getServer();
        EntityPlayerMP player = server == null ? null
                : server.getConfigurationManager().getPlayer(playerId);
        if (player != null && session.dimensionInventorySnapshot == null) {
            session.dimensionInventorySnapshot =
                    PossessionPlayerInventory.captureTemporary(player);
            PossessionPlayerInventory.persistMobInventory(player, mob);
        }
        boolean explodedCreeper = mob instanceof EntityCreeper && session.creeperFuse
                && fr.vanillainstincts.compat.Minecraft112Compat.removed(mob);
        if (mob.getHealth() <= 0.0F || explodedCreeper) {
            BY_MOB.remove(mob.getUniqueID());
            BY_PLAYER.remove(playerId);
            if (player != null) {
                restoreControllerInventory(player, session);
                restoreControllerBodyState(player, session);
            }
            restoreMob(session);
            if (player != null) {
                resetCameraAndNotify(player, StopReason.MOB_DIED);
                killController(player);
            }
            return;
        }
        session.dimensionTransferPending = true;
        // Keep READY if a destination join event arrived before this late
        // source-level leave event. Normal leave-first transfers start false.
        session.dimensionTransferGraceTicks = DIMENSION_TRANSFER_GRACE_TICKS;
        session.breakPos = null;
        session.breakProgress = 0.0F;
        session.bowDrawing = false;
        session.bowHand = null;
    }
    /** Rebind possession to the same mob after a portal/dimension transfer. */
    public static void onMobJoin(EntityLiving mob) {
        if (mob == null || mob.worldObj.isRemote) return;
        if (!fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(mob.getEntityData(), POSSESSION_CONTROLLER_KEY)) return;
        UUID playerId = fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(mob.getEntityData(), POSSESSION_CONTROLLER_KEY);
        Session session = BY_PLAYER.get(playerId);
        if (session == null) {
            // A stale marker must never create a possession after a restart.
            mob.getEntityData().removeTag(POSSESSION_CONTROLLER_KEY);
            return;
        }
        EntityLiving previous = session.mob;
        if (previous != mob) {
            if (previous != null) BY_MOB.remove(previous.getUniqueID());
            session.mob = mob;
            BY_MOB.put(mob.getUniqueID(), playerId);
        }
        session.dimensionTransferPending = true;
        session.dimensionTransferReady = true;
        session.dimensionTransferGraceTicks = DIMENSION_TRANSFER_GRACE_TICKS;
        session.breakPos = null;
        session.breakProgress = 0.0F;
        session.bowDrawing = false;
        session.bowHand = null;
        // World interaction is deferred to the next tick after EntityJoinLevelEvent.
    }
    private static void completeDimensionTransfer(EntityPlayerMP player,
                                                  Session session) {
        EntityLiving mob = session.mob;
        if (!(mob.worldObj instanceof WorldServer)) return; WorldServer targetLevel = (WorldServer) (mob.worldObj);
        session.dimensionTransferPending = false;
        session.dimensionTransferReady = false;
        session.dimensionTransferGraceTicks = 0;
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(mob.getEntityData(), POSSESSION_CONTROLLER_KEY,
                player.getUniqueID());
        session.goalsSuppressed = false;
        mob.getNavigator().stop();
        mob.setDeltaMovement(new Vec3(0.0D, 0.0D, 0.0D));
        mob.setXxa(0.0F);
        mob.setYya(0.0F);
        mob.setZza(0.0F);
        mob.setAttackTarget(null);
        mob.setAggressive(false);
        mob.setNoAi(false);
        suppressGoals(session, true);
        if (session.dimensionInventorySnapshot == null) {
            session.dimensionInventorySnapshot =
                    PossessionPlayerInventory.captureTemporary(player);
        }
        if (player.getLevel() != targetLevel) {
            fr.vanillainstincts.compat.Minecraft112Compat.teleport(player, targetLevel, mob.getX(), mob.getY(), mob.getZ(),
                    mob.yRot, mob.xRot);
        }
        PossessionPlayerInventory.restoreTemporary(player,
                session.dimensionInventorySnapshot);
        session.dimensionInventorySnapshot = null;
        player.noPhysics = true;
        player.setInvisible(true);
        player.setInvulnerable(true);
        PossessionPlayerInventory.syncEquipment(player, mob);
        if (player.isCreative()) {
            mob.setInvulnerable(true);
            mob.setNoGravity(true);
        } else {
            mob.setInvulnerable(session.previousMobInvulnerable);
            applyPossessionGravity(session, true);
        }
        syncControllerBody(player, mob);
        player.setCamera(mob);
        VanillaInstinctsNetwork.sendToPlayer(player,
                new PossessionStatePayload(true, mob.getEntityId()));
    }
    public static void clearLevel(WorldServer level) {
        if (level == null || BY_PLAYER.isEmpty()) return;
        Iterator<Map.Entry<UUID, Session>> iterator = BY_PLAYER.entrySet()
                .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            Session session = entry.getValue();
            if (session.mob.worldObj != level) continue;
            BY_MOB.remove(session.mob.getUniqueID());
            EntityPlayerMP player = level.getServer().getPlayerList()
                    .getPlayer(entry.getKey());
            if (player != null) {
                restoreControllerInventory(player, session);
                restoreControllerBodyState(player, session);
            }
            restoreMob(session);
            if (player != null) resetCameraAndNotify(player, StopReason.LEVEL_UNLOAD);
            iterator.remove();
        }
    }
    private static boolean start(EntityPlayerMP player, EntityLiving mob) {
        if (!VanillaInstinctsServerConfig.possessionEnabled()) {
            message(player, "message.vanillainstincts.possession.disabled");
            return false;
        }
        if (!fr.vanillainstincts.compat.Minecraft17Compat.isSpectator(player)) {
            message(player, "message.vanillainstincts.possession.spectator_only");
            return false;
        }
        if (mob == null || player.getCamera() != mob || !mob.isEntityAlive()
                || fr.vanillainstincts.compat.Minecraft112Compat.removed(mob) || mob.worldObj != player.getLevel()) {
            message(player, "message.vanillainstincts.possession.camera_required");
            return false;
        }
        if (VanillaMobPossessionCatalog.isVanilla(mob)) {
            if (!VanillaMobPossessionCatalog.coversVanillaMob(mob)) {
                message(player, "message.vanillainstincts.possession.unsupported_target");
                return false;
            }
        } else if (!VanillaInstinctsServerConfig.possessionModdedMobsEnabled()) {
            message(player, "message.vanillainstincts.possession.modded_disabled");
            return false;
        }
        if (BY_MOB.containsKey(mob.getUniqueID())) {
            message(player, "message.vanillainstincts.possession.already_controlled");
            return false;
        }
        Session session = new Session(mob, mob.isNoAi(), mob.isNoGravity(),
                mob.isInvulnerable(), mob.isAggressive(), mob.getAttackTarget(),
                mob.yRot, mob.xRot,
                copyDisabledFlags(goalSelector(mob)),
                copyDisabledFlags(targetSelector(mob)),
                currentDragonPhase(mob));
        BY_PLAYER.put(player.getUniqueID(), session);
        BY_MOB.put(mob.getUniqueID(), player.getUniqueID());
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(mob.getEntityData(), POSSESSION_CONTROLLER_KEY,
                player.getUniqueID());
        session.inventorySnapshot = PossessionPlayerInventory.enter(player, mob);
        session.previousPlayerInvisible = player.isInvisible();
        session.previousPlayerInvulnerable = player.isInvulnerable();
        session.previousPlayerHealth = player.getHealth();
        session.previousPlayerAbsorption = player.getAbsorptionAmount();
        // The controller player is only an input/menu proxy while possessed.
        // It must not collide with, push, be targeted, or take damage separately
        // from the mob whose health is shown to the user.
        player.noPhysics = true;
        player.setInvisible(true);
        player.setInvulnerable(true);
        // Keep the hidden proxy itself out of Survival health mechanics. Hunger
        // remains real and persistent, but natural regeneration/starvation are
        // applied to the controlled mob by tickPossessionFood().
        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0.0F);
        // Spectator is only the selection/entry mode. Once possession starts,
        // promote the hidden controller proxy to real Survival semantics so
        // vanilla menus and world interactions are fully mutable: container
        // clicks, cursor stacks, drag, shift-click, crafting, furnaces, etc.
        // The camera remains attached to the mob and the proxy body stays
        // invisible/invulnerable/non-physical. A later explicit switch back to
        // Spectator is handled by onPlayerGameModeChange() as the release path.
        player.setGameMode(GameType.SURVIVAL);
        mob.getNavigator().stop();
        mob.setDeltaMovement(new Vec3(0.0D, 0.0D, 0.0D));
        mob.setXxa(0.0F);
        mob.setYya(0.0F);
        mob.setZza(0.0F);
        mob.setAttackTarget(null);
        mob.setAggressive(false);
        // Strict ownership: ordinary mobs never get a temporary AI pulse while
        // a player is driving them.  Environmental/entity ticks still run, but
        // GoalSelector and Brain cannot fight the controller for movement.
        mob.setNoAi(false);
        suppressGoals(session, true);
        applyPossessionGravity(session, true);
        player.setCamera(mob);
        VanillaInstinctsNetwork.sendToPlayer(player,
                new PossessionStatePayload(true, mob.getEntityId()));
        player.displayClientMessage(new net.minecraft.util.text.TextComponentTranslation(
                "message.vanillainstincts.possession.started",
                mob.getDisplayName()), true);
        return true;
    }

    private static void stop(EntityPlayerMP player, StopReason reason) {
        if (player == null) return;
        Session session = BY_PLAYER.remove(player.getUniqueID());
        if (session == null) {
            if (reason == StopReason.PLAYER_REQUEST) {
                VanillaInstinctsNetwork.sendToPlayer(player,
                        new PossessionStatePayload(false, -1));
            }
            return;
        }
        BY_MOB.remove(session.mob.getUniqueID());
        restoreControllerInventory(player, session);
        restoreControllerBodyState(player, session);
        restoreMob(session);
        // Every release path returns the camera to the real player. The command
        // additionally switches the player to Spectator after this method;
        // /gamemode spectator performs that game-mode change itself immediately
        // after the loader pre-change event returns.
        resetCameraAndNotify(player, reason);
    }

    private static void notifyStopped(EntityPlayerMP player, StopReason reason) {
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        VanillaInstinctsNetwork.sendToPlayer(player,
                new PossessionStatePayload(false, -1));
        if (reason.visible) {
            player.displayClientMessage(new net.minecraft.util.text.TextComponentTranslation(
                    reason.translationKey), true);
        }
    }

    private static void resetCameraAndNotify(EntityPlayerMP player,
                                             StopReason reason) {
        // Deactivate the client possession guard BEFORE vanilla's camera packet
        // arrives. Otherwise the client-side keep-camera mixin can correctly
        // reject the reset because it still believes the session is active.
        notifyStopped(player, reason);
        if (player.getCamera() != player) player.setCamera(player);
    }

    private static void restoreControllerInventory(EntityPlayerMP player,
                                                   Session session) {
        if (session.inventorySnapshot == null) return;
        PossessionPlayerInventory.saveMobInventory(player, session.mob);
        PossessionPlayerInventory.restore(player, session.inventorySnapshot);
        session.inventorySnapshot = null;
    }

    private static void restoreControllerBodyState(EntityPlayerMP player,
                                                   Session session) {
        player.setInvisible(session.previousPlayerInvisible);
        player.setInvulnerable(session.previousPlayerInvulnerable);
        player.setHealth(Math.min(player.getMaxHealth(),
                Math.max(0.1F, session.previousPlayerHealth)));
        player.setAbsorptionAmount(Math.max(0.0F,
                session.previousPlayerAbsorption));
        // Spectator naturally uses noPhysics; normal modes should regain their
        // ordinary collision body after possession ends.
        player.noPhysics = fr.vanillainstincts.compat.Minecraft17Compat.isSpectator(player);
    }

    private static void restoreMob(Session session) {
        EntityLiving mob = session.mob;
        if (mob == null) return;
        mob.getEntityData().removeTag(POSSESSION_CONTROLLER_KEY);
        if (fr.vanillainstincts.compat.Minecraft112Compat.removed(mob)) return;
        if (session.bowDrawing && mob instanceof EntitySkeleton) { EntitySkeleton skeleton = (EntitySkeleton) (mob); 
            skeleton.stopUsingItem();
            session.bowDrawing = false;
            session.bowHand = null;
        }
        if (mob instanceof EntityCreeper) { EntityCreeper creeper = (EntityCreeper) (mob); creeper.setSwellDir(-1); }
        mob.setXxa(0.0F);
        mob.setYya(0.0F);
        mob.setZza(0.0F);
        mob.setSprinting(false);
        mob.setShiftKeyDown(false);
        if (mob instanceof EntityDragon
                && session.previousDragonPhase != null) { EntityDragon dragon = (EntityDragon) (mob); 
            dragon.getPhaseManager().setPhase(session.previousDragonPhase);
        }
        restoreDisabledFlags(goalSelector(mob), session.previousGoalDisabled);
        restoreDisabledFlags(targetSelector(mob), session.previousTargetDisabled);
        mob.setNoGravity(session.previousNoGravity);
        mob.setInvulnerable(session.previousMobInvulnerable);
        mob.setNoAi(session.previousNoAi);
        mob.setAggressive(session.previousAggressive);
        if (session.previousTarget != null && session.previousTarget.isEntityAlive()
                && session.previousTarget.worldObj == mob.worldObj) {
            mob.setAttackTarget(session.previousTarget);
        } else {
            mob.setAttackTarget(null);
        }
    }

    private static boolean valid(EntityPlayerMP player, EntityLiving mob) {
        // Spectator is required only to ENTER possession. Once controlling the
        // mob, changing to Survival/Creative/Adventure must not tear down the
        // session; only mob/player death, removal, dimension mismatch or an
        // explicit transition back to Spectator ends it.
        return player.isEntityAlive()
                && mob != null && mob.isEntityAlive() && !fr.vanillainstincts.compat.Minecraft112Compat.removed(mob)
                && player.getLevel() == mob.worldObj;
    }

    private static void applyControl(EntityPlayerMP player, Session session) {
        EntityLiving mob = session.mob;
        // Game-mode changes can rewrite player abilities/body flags. The hidden
        // controller stays a non-physical, invulnerable proxy until release.
        player.noPhysics = true;
        player.setInvisible(true);
        player.setInvulnerable(true);
        // Only the possessed mob is allowed to drive portal transitions. The
        // proxy follows it explicitly after the mob joins the destination.
        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0.0F);
        boolean creativeControl = player.isCreative();
        applyMovementExhaustion(player, session, creativeControl);
        // Creative possession applies Creative survivability to the controlled
        // body itself. Returning to Survival restores the mob's original
        // invulnerability state without ending the possession session.
        mob.setInvulnerable(creativeControl || session.previousMobInvulnerable);
        // Reassert strict ownership every tick in case another mod toggles AI.
        mob.setNoAi(false);
        suppressGoals(session, true);
        if (creativeControl) {
            mob.setNoGravity(true);
        } else {
            applyPossessionGravity(session, true);
        }
        mob.getNavigator().stop();
        mob.setAttackTarget(null);
        mob.setAggressive(false);
        PossessionPlayerInventory.syncEquipment(player, mob);

        mob.yRot = session.yaw;
        mob.xRot = session.pitch;
        mob.setYHeadRot(session.yaw);
        mob.setYBodyRot(session.yaw);
        syncControllerBody(player, mob);

        float forward = axis(session.flags, PossessionInputPayload.FORWARD,
                PossessionInputPayload.BACK);
        float strafe = axis(session.flags, PossessionInputPayload.LEFT,
                PossessionInputPayload.RIGHT);
        boolean jump = has(session.flags, PossessionInputPayload.JUMP);
        boolean sneak = has(session.flags, PossessionInputPayload.SNEAK);
        boolean descend = has(session.flags, PossessionInputPayload.DESCEND);
        // Sprint is a player control, not an AI capability. Several vanilla
        // EntityLiving implementations report canSprint() == false even though their
        // body can physically move faster, which made the possession key feel
        // broken. Preserve the mob's base movement attribute and apply the
        // normal player sprint multiplier whenever the key is held.
        boolean sprint = has(session.flags, PossessionInputPayload.SPRINT)
                && !sneak;
        mob.setShiftKeyDown(sneak);
        mob.setSprinting(sprint);

        VanillaMobPossessionCatalog.Movement movement =
                VanillaMobPossessionCatalog.movement(mob);
        boolean free3d = movement == VanillaMobPossessionCatalog.Movement.FLYING
                || (movement == VanillaMobPossessionCatalog.Movement.AQUATIC
                || movement == VanillaMobPossessionCatalog.Movement.AMPHIBIOUS)
                && mob.isInWaterOrBubble();
        if (creativeControl) {
            steerCreativeFlight(mob, forward, strafe, jump, descend, sprint);
        } else if (movement == VanillaMobPossessionCatalog.Movement.DRAGON
                && mob instanceof EntityDragon) { EntityDragon dragon = (EntityDragon) (mob); 
            steerDragon(dragon, forward, strafe, jump, descend, sprint);
        } else if (movement == VanillaMobPossessionCatalog.Movement.STATIONARY) {
            mob.getNavigator().stop();
            mob.setDeltaMovement(new Vec3(0.0D, 0.0D, 0.0D));
        } else if (free3d) {
            steerThreeDimensional(mob, forward, strafe, jump, descend, sprint);
        } else if (movement == VanillaMobPossessionCatalog.Movement.AQUATIC) {
            // Pure swimmers retain their real out-of-water physics.
            mob.getNavigator().stop();
        } else {
            steerGround(player, session, forward, strafe, jump, sprint, sneak,
                    movement);
        }

        pickupNearbyItems(player, session);
        handleContinuousActions(player, session);
        long gameTime = mob.worldObj.getTotalWorldTime();
        if (gameTime >= session.nextInventoryPersistAt) {
            PossessionPlayerInventory.persistMobInventory(player, mob);
            session.nextInventoryPersistAt = gameTime + 20L;
        }
        session.lastFoodSprint = sprint;
        session.jumpHeld = jump;
        applyHungerEffectExhaustion(player, session, creativeControl);
    }

    private static void steerCreativeFlight(EntityLiving mob, float forward,
                                            float strafe, boolean ascend,
                                            boolean descend, boolean sprint) {
        // Creative flight is deliberately independent from the mob species:
        // even ground, aquatic and normally stationary mobs can fly while their
        // controller is in Creative, just like a Creative player can.
        Vec3 forwardVector = Vec3.directionFromRotation(0.0F, mob.yRot);
        Vec3 right = new Vec3(-forwardVector.zCoord, 0.0D, forwardVector.xCoord);
        Vec3 desired = fr.vanillainstincts.compat.Minecraft112Compat.scale(forwardVector, forward)
                .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(right, -strafe))
                .add(0.0D, ascend ? 1.0D : descend ? -1.0D : 0.0D, 0.0D);
        if (desired.lengthSqr() < 1.0E-5D) {
            mob.setDeltaMovement(new Vec3(0.0D, 0.0D, 0.0D));
            return;
        }
        double speed = sprint ? 0.45D : 0.25D;
        mob.setDeltaMovement(fr.vanillainstincts.compat.Minecraft112Compat.scale(desired.normalize(), speed));
    }

    private static void steerThreeDimensional(EntityLiving mob, float forward,
                                                  float strafe,
                                                  boolean ascend,
                                                  boolean descend,
                                                  boolean sprint) {
        Vec3 look = mob.getLookVec();
        Vec3 horizontalRight = new Vec3(-look.zCoord, 0.0D, look.xCoord);
        if (horizontalRight.lengthSqr() > 1.0E-6D) {
            horizontalRight = horizontalRight.normalize();
        }
        Vec3 desired = fr.vanillainstincts.compat.Minecraft112Compat.scale(look, forward)
                .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(horizontalRight, -strafe))
                .add(0.0D, ascend ? 1.0D : descend ? -1.0D : 0.0D, 0.0D);
        if (desired.lengthSqr() < 1.0E-5D) {
            // Flying mobs hover; swimmers retain their natural water physics.
            if (VanillaMobPossessionCatalog.movement(mob)
                    == VanillaMobPossessionCatalog.Movement.FLYING) {
                mob.setDeltaMovement(new Vec3(0.0D, 0.0D, 0.0D));
            } else {
                Vec3 current = mob.getMotion();
                mob.setDeltaMovement(current.xCoord * 0.55D, current.yCoord,
                        current.zCoord * 0.55D);
            }
            return;
        }
        desired = desired.normalize();
        double speed = movementSpeed(mob) * (sprint ? 1.30D : 1.0D);
        mob.setDeltaMovement(fr.vanillainstincts.compat.Minecraft112Compat.scale(desired, speed));
    }

    private static void steerGround(EntityPlayerMP player, Session session,
                                    float forward, float strafe, boolean jump,
                                    boolean sprint, boolean sneak,
                                    VanillaMobPossessionCatalog.Movement movement) {
        EntityLiving mob = session.mob;
        Vec3 forwardVector = Vec3.directionFromRotation(0.0F, mob.yRot);
        Vec3 right = new Vec3(-forwardVector.zCoord, 0.0D, forwardVector.xCoord);
        Vec3 desired = fr.vanillainstincts.compat.Minecraft112Compat.scale(forwardVector, forward).add(fr.vanillainstincts.compat.Minecraft112Compat.scale(right, -strafe));
        Vec3 current = mob.getMotion();
        if (desired.lengthSqr() > 1.0E-5D) {
            desired = desired.normalize();
            double multiplier = sprint ? 1.30D : sneak ? 0.30D : 1.0D;
            double speed = movementSpeed(mob) * multiplier;
            double dx = desired.xCoord * speed;
            double dz = desired.zCoord * speed;
            if (sneak && mob.onGround) {
                Vec3 safe = keepSneakingOnLedge(mob, dx, dz);
                dx = safe.xCoord;
                dz = safe.zCoord;
            }
            mob.setDeltaMovement(dx, current.yCoord, dz);
        } else {
            mob.setDeltaMovement(current.xCoord * 0.55D, current.yCoord,
                    current.zCoord * 0.55D);
        }

        String species = VanillaMobPossessionCatalog.path(mob);
        boolean autoHop = "slime".equals(species) || "magma_cube".equals(species)
                || "rabbit".equals(species) || "frog".equals(species);
        boolean moving = Math.abs(forward) > 0.01F || Math.abs(strafe) > 0.01F;
        if (!sneak && mob.onGround && ((jump && !session.jumpHeld)
                || (autoHop && moving))) {
            mob.getJumpControl().jump();
            player.getFoodData().addExhaustion(sprint ? 0.20F : 0.05F);
        } else if (movement == VanillaMobPossessionCatalog.Movement.CLIMBING
                && mob.horizontalCollision && (jump || forward > 0.0F)) {
            Vec3 velocity = mob.getMotion();
            mob.setDeltaMovement(velocity.xCoord, Math.max(velocity.yCoord, 0.20D),
                    velocity.zCoord);
        }
    }

    private static Vec3 keepSneakingOnLedge(EntityLiving mob, double dx, double dz) {
        AxisAlignedBB box = mob.getBoundingBox();
        double safeX = dx;
        double safeZ = dz;
        // EntityPlayer-style edge safety: if a proposed horizontal component has no
        // collision support roughly half a block below, suppress that component.
        if (mob.fr.vanillainstincts.compat.Minecraft112Compat.noCollision(world, mob, box.move(dx, -0.55D, 0.0D))) {
            safeX = 0.0D;
        }
        if (mob.fr.vanillainstincts.compat.Minecraft112Compat.noCollision(world, mob, box.move(0.0D, -0.55D, dz))) {
            safeZ = 0.0D;
        }
        if (mob.fr.vanillainstincts.compat.Minecraft112Compat.noCollision(world, mob, box.move(safeX, -0.55D, safeZ))) {
            safeX = 0.0D;
            safeZ = 0.0D;
        }
        return new Vec3(safeX, 0.0D, safeZ);
    }


    /** Apply the same distance-based exhaustion costs used by a player body. */
    private static void applyMovementExhaustion(EntityPlayerMP player,
                                                Session session,
                                                boolean creativeControl) {
        Vec3 current = session.fr.vanillainstincts.compat.Minecraft17Compat.position(mob);
        Vec3 previous = session.lastFoodPosition;
        session.lastFoodPosition = current;
        if (previous == null || creativeControl) return;

        double dx = current.xCoord - previous.xCoord;
        double dy = current.yCoord - previous.yCoord;
        double dz = current.zCoord - previous.zCoord;
        float exhaustion = 0.0F;
        if (session.mob.isInWaterOrBubble()) {
            exhaustion = (float) (Math.sqrt(dx * dx + dy * dy + dz * dz)
                    * 0.01D);
        } else if (session.lastFoodSprint && session.mob.onGround) {
            exhaustion = (float) (Math.sqrt(dx * dx + dz * dz) * 0.10D);
        }
        if (exhaustion > 0.0F) {
            player.getFoodData().addExhaustion(exhaustion);
        }
    }

    private static void applyHungerEffectExhaustion(EntityPlayerMP player,
                                                    Session session,
                                                    boolean creativeControl) {
        if (creativeControl || !session.mob.hasEffect(MobEffects.HUNGER)) return;
        PotionEffect hunger = session.mob.getEffect(MobEffects.HUNGER);
        if (hunger != null) {
            player.getFoodData().addExhaustion(
                    0.005F * (hunger.getAmplifier() + 1));
        }
    }

    /**
     * Vanilla-style hunger healing/starvation, but with the possessed mob as the
     * health body and the controller's real FoodStats as the hunger state.
     */
    private static void tickPossessionFood(EntityPlayerMP player, Session session,
                                           boolean creativeControl) {
        if (creativeControl || !(session.mob.worldObj instanceof WorldServer)) {
            session.foodTickTimer = 0;
            return;
        } WorldServer level = (WorldServer) (session.mob.worldObj);

        EntityLiving mob = session.mob;
        FoodStats food = player.getFoodData();
        int foodLevel = food.getFoodLevel();
        float saturation = food.getSaturationLevel();
        boolean hurt = mob.getHealth() < mob.getMaxHealth();
        boolean naturalRegen = level.getGameRules().getGameRuleBooleanValue(
                GameRules.RULE_NATURAL_REGENERATION);

        if (naturalRegen && saturation > 0.0F && foodLevel >= 20 && hurt) {
            session.foodTickTimer++;
            if (session.foodTickTimer >= 10) {
                float usedSaturation = Math.min(saturation, 6.0F);
                mob.heal(usedSaturation / 6.0F);
                food.addExhaustion(usedSaturation);
                session.foodTickTimer = 0;
            }
        } else if (naturalRegen && foodLevel >= 18 && hurt) {
            session.foodTickTimer++;
            if (session.foodTickTimer >= 80) {
                mob.heal(1.0F);
                food.addExhaustion(6.0F);
                session.foodTickTimer = 0;
            }
        } else if (foodLevel <= 0) {
            session.foodTickTimer++;
            if (session.foodTickTimer >= 80) {
                Difficulty difficulty = level.difficultySetting;
                float health = mob.getHealth();
                if (health > 10.0F || difficulty == Difficulty.HARD
                        || health > 1.0F && difficulty == Difficulty.NORMAL) {
                    mob.hurt(DamageSource.STARVE, 1.0F);
                }
                session.foodTickTimer = 0;
            }
        } else {
            session.foodTickTimer = 0;
        }
    }

    private static double movementSpeed(EntityLiving mob) {
        if (mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED) == null) return 0.10D;
        return MathHelper.clamp(mob.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getValue(),
                0.02D, 1.50D);
    }

    private static void applyPossessionGravity(Session session,
                                                boolean manualControl) {
        if (!manualControl) {
            session.mob.setNoGravity(session.previousNoGravity);
            return;
        }
        VanillaMobPossessionCatalog.Movement movement =
                VanillaMobPossessionCatalog.movement(session.mob);
        if (movement == VanillaMobPossessionCatalog.Movement.FLYING) {
            session.mob.setNoGravity(true);
        } else {
            session.mob.setNoGravity(session.previousNoGravity);
        }
    }

    private static void steerDragon(EntityDragon dragon, float forward,
                                    float strafe, boolean ascend,
                                    boolean descend, boolean sprint) {
        Vec3 look = dragon.getLookVec().normalize();
        Vec3 right = new Vec3(-look.zCoord, 0.0D, look.xCoord);
        if (right.lengthSqr() > 1.0E-6D) right = right.normalize();
        Vec3 desired = fr.vanillainstincts.compat.Minecraft112Compat.scale(look, forward)
                .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(right, -strafe))
                .add(0.0D, ascend ? 1.0D : descend ? -1.0D : 0.0D, 0.0D);
        if (desired.lengthSqr() < 1.0E-5D) {
            dragon.getPhaseManager().setPhase(PhaseType.HOVERING);
            return;
        }
        desired = desired.normalize();
        double lead = sprint ? 28.0D : 18.0D;
        Vec3 wanted = fr.vanillainstincts.compat.Minecraft17Compat.position(dragon).add(fr.vanillainstincts.compat.Minecraft112Compat.scale(desired, lead));
        dragon.getPhaseManager().setPhase(PhaseType.CHARGING_PLAYER);
        PhaseChargingPlayer phase = dragon.getPhaseManager()
                .getPhase(PhaseType.CHARGING_PLAYER);
        phase.setAttackTarget(wanted);
    }

    private static void handleContinuousActions(EntityPlayerMP player,
                                                Session session) {
        boolean attack = has(session.flags, PossessionInputPayload.ATTACK);
        boolean use = has(session.flags, PossessionInputPayload.USE);

        if (attack) {
            EntityEnderCrystal crystal = PossessionAim.findEndCrystal(session.mob,
                    PLAYER_INTERACTION_RANGE);
            EntityLivingBase aimed = PossessionAim.findLiving(session.mob, player,
                    PLAYER_INTERACTION_RANGE);
            if (crystal != null || aimed != null) {
                resetMining(player, session);
                // Every possessed mob keeps a player-like manual attack path.
                // End Crystals are entities but not EntityLivingBase instances, so
                // they are selected separately and attacked through EntityPlayer#attack.
                primary(player, session);
            } else {
                mineLookedAtBlock(player, session);
            }
        } else {
            resetMining(player, session);
        }

        boolean abilities = VanillaInstinctsServerConfig.possessionAbilitiesEnabled();
        boolean emptyMainHand = fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(player.getHeldItem());
        if (use && !session.useHeld) {
            // Entity interaction always has priority over the possessed
            // mob's own right-click ability. This keeps villager trading,
            // entity menus and similar player interactions usable for every
            // controlled species, including Creepers, Skeletons, Witches
            // and Endermen. Keep the decision latched until the key is
            // released so a held click cannot start the mob ability one tick
            // after opening the interaction.
            session.useInteractionConsumed = playerLikeEntityUse(player, session);
        } else if (!use) {
            session.useInteractionConsumed = false;
        }
        boolean creeperFuseControl = abilities && emptyMainHand
                && !session.useInteractionConsumed
                && session.mob instanceof EntityCreeper;
        if (session.mob instanceof EntityCreeper) { EntityCreeper creeper = (EntityCreeper) (session.mob); 
            boolean fuseNow = creeperFuseControl && use;
            creeper.setSwellDir(fuseNow ? 1 : -1);
            session.creeperFuse = fuseNow;
        }

        EnumHand skeletonBow = PossessionSkeletonBowController.bowHand(session.mob);
        if (session.useInteractionConsumed) {
            if (session.bowDrawing) cancelSkeletonBow(session);
        } else if (use && skeletonBow != null) {
            if (!session.bowDrawing) beginSkeletonBow(session, skeletonBow);
            else session.mob.setAggressive(true);
        } else if (!use && session.useHeld && session.bowDrawing) {
            releaseSkeletonBow(player, session);
        } else if (creeperFuseControl) {
            // Holding empty-hand right click directly drives EntityCreeper swell.
            // Releasing the button above sets the direction back to defusing.
        } else if (use && !session.useHeld) {
            boolean manualMobAbility = abilities && emptyMainHand
                    && (session.mob instanceof EntityEnderman
                    || session.mob instanceof EntityWitch);
            if (manualMobAbility) secondary(player, session);
            else {
                boolean consumed = playerLikeUse(player, session);
                if (!consumed && abilities) secondary(player, session);
            }
        } else if (!use && session.useHeld && player.isUsingItem()) {
            withPlayerProxy(player, session.mob, () -> {
                player.releaseUsingItem();
                return Boolean.TRUE;
            });
        }
        if (session.bowDrawing && PossessionSkeletonBowController.bowHand(session.mob) == null) {
            cancelSkeletonBow(session);
        }
        session.attackHeld = attack;
        session.useHeld = use;
    }

    private static void mineLookedAtBlock(EntityPlayerMP player, Session session) {
        EntityLiving mob = session.mob;
        MovingObjectPosition hit = mob.pick(PLAYER_INTERACTION_RANGE, 1.0F, false);
        if (!(hit instanceof BlockRayTraceResult)
                || hit.getType() != MovingObjectPosition.MovingObjectType.BLOCK) {
            resetMining(player, session);
            return;
        } BlockRayTraceResult blockHit = (BlockRayTraceResult) (hit);
        BlockPos pos = blockHit.getBlockPos();
        WorldServer level = player.getLevel();
        LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos);
        if (state.isAir()) {
            resetMining(player, session);
            return;
        }
        if (!pos.equals(session.breakPos)) {
            resetMining(player, session);
            session.breakPos = immutableBlockPos(pos);
            session.breakProgress = 0.0F;
        }

        float amount = player.isCreative() ? 1.0F : withPlayerProxy(player, mob,
                () -> state.getDestroyProgress(player, level, pos));
        if (amount <= 0.0F) return;
        session.breakProgress += amount;
        int stage = MathHelper.clamp((int) (session.breakProgress * 10.0F), 0, 9);
        level.destroyBlockProgress(mob.getEntityId(), pos, stage);
        // Keep the real EntityLivingBase swing state alive while mining so the
        // possessed arm/item animates instead of remaining frozen.
        long gameTime = level.getTotalWorldTime();
        if (gameTime >= session.nextMiningSwingAt) {
            mob.swingItem();
            session.nextMiningSwingAt = gameTime + 4L;
        }
        if (session.breakProgress < 1.0F) return;

        boolean destroyed = withPlayerProxy(player, mob,
                () -> player.gameMode.destroyBlock(pos));
        level.destroyBlockProgress(mob.getEntityId(), pos, -1);
        session.breakPos = null;
        session.breakProgress = 0.0F;
        if (destroyed) {
            PossessionPlayerInventory.syncEquipment(player, mob);
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void resetMining(EntityPlayerMP player, Session session) {
        if (session.breakPos != null && session.mob.worldObj instanceof WorldServer) { WorldServer level = (WorldServer) (session.mob.worldObj); 
            level.destroyBlockProgress(session.mob.getEntityId(), session.breakPos, -1);
        }
        session.breakPos = null;
        session.breakProgress = 0.0F;
        session.nextMiningSwingAt = 0L;
    }

    /**
     * EntityPlayer-style item pickup using the possessed mob's collision body.
     * The hidden controller owns the temporary possession inventory, so
     * delegating to EntityItem.playerTouch preserves vanilla pickup delay,
     * ownership checks, pickup events, sounds, statistics and stack merging.
     */
    private static void pickupNearbyItems(EntityPlayerMP player, Session session) {
        EntityLiving mob = session.mob;
        if (!(mob.worldObj instanceof WorldServer)) return; WorldServer level = (WorldServer) (mob.worldObj);
        AxisAlignedBB pickupBox = mob.getBoundingBox().inflate(0.35D, 0.20D, 0.35D);
        boolean changed = false;
        for (EntityItem item : level.getEntitiesOfClass(EntityItem.class,
                pickupBox, entity -> entity.isEntityAlive() && !entity.getItem().isEmpty())) {
            int before = item.getEntityItem().stackSize;
            withPlayerProxy(player, mob, () -> {
                item.playerTouch(player);
                return Boolean.TRUE;
            });
            if (!item.isEntityAlive() || fr.vanillainstincts.compat.Minecraft112Compat.removed(item)
                    || item.getEntityItem().stackSize != before) {
                changed = true;
            }
        }
        if (changed) {
            PossessionPlayerInventory.syncEquipment(player, mob);
            player.inventoryMenu.broadcastChanges();
            PossessionPlayerInventory.persistMobInventory(player, mob);
        }
    }

    private static boolean playerLikeEntityUse(EntityPlayerMP player, Session session) {
        EntityLiving mob = session.mob;
        EntityLivingBase entityTarget = PossessionAim.findLiving(mob, player, PLAYER_INTERACTION_RANGE);
        if (entityTarget == null) return false;
        boolean interacted = withPlayerProxy(player, mob, () ->
                Boolean.valueOf(player.interactWith(entityTarget))).booleanValue();
        if (!interacted) return false;
        PossessionPlayerInventory.syncEquipment(player, mob);
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    /** Fishing rods use the hidden controller as their vanilla angler. */
    private static boolean isFishingRod(ItemStack stack) {
        return stack != null && !(fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack))
                && stack.getItem().equals(Items.fishing_rod);
    }

    private static boolean playerLikeUse(EntityPlayerMP player, Session session) {
        EntityLiving mob = session.mob;
        EnumActionResult result = EnumActionResult.PASS;
        // Fishing is player-owned in vanilla. While possessing a mob,
        // the hidden controller is the angler and is snapped to the mob
        // before the item action. Give rods their air-use path before
        // ordinary block use so cast/reel remains reliable.
        if (isFishingRod(player.getHeldItem(EnumHand.MAIN_HAND))) {
            result = useInAir(player, mob, EnumHand.MAIN_HAND);
        } else if (isFishingRod(player.getHeldItem(EnumHand.OFF_HAND))) {
            result = useInAir(player, mob, EnumHand.OFF_HAND);
        }
        if (result == EnumActionResult.PASS && playerLikeEntityUse(player, session)) return true;
        MovingObjectPosition hit = mob.pick(PLAYER_INTERACTION_RANGE, 1.0F, false);
        if (result == EnumActionResult.PASS && hit instanceof BlockRayTraceResult
                && hit.getType() == MovingObjectPosition.MovingObjectType.BLOCK) { BlockRayTraceResult blockHit = (BlockRayTraceResult) (hit); 
            result = useOnBlock(player, mob, blockHit, EnumHand.MAIN_HAND);
            if (result == EnumActionResult.PASS) {
                result = useOnBlock(player, mob, blockHit, EnumHand.OFF_HAND);
            }
        }
        if (result == EnumActionResult.PASS) {
            result = useInAir(player, mob, EnumHand.MAIN_HAND);
            if (result == EnumActionResult.PASS) {
                result = useInAir(player, mob, EnumHand.OFF_HAND);
            }
        }
        PossessionPlayerInventory.syncEquipment(player, mob);
        player.inventoryMenu.broadcastChanges();
        return result != EnumActionResult.PASS;
    }

    private static EnumActionResult useOnBlock(EntityPlayerMP player, EntityLiving mob,
                                                 BlockRayTraceResult hit,
                                                 EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        return withPlayerProxy(player, mob, () -> player.gameMode.useItemOn(
                player, player.getLevel(), stack, hand, hit));
    }

    private static EnumActionResult useInAir(EntityPlayerMP player, EntityLiving mob,
                                               EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return EnumActionResult.PASS;
        return withPlayerProxy(player, mob, () -> player.gameMode.useItem(
                player, player.getLevel(), stack, hand));
    }

    private static <T> T withPlayerProxy(EntityPlayerMP player, EntityLiving mob,
                                         Supplier<T> action) {
        ServerPlayerGameModeAccessor gameMode =
                (ServerPlayerGameModeAccessor) player.gameMode;
        GameType previousGameType = gameMode.vanillaInstincts$getGameModeForPlayer();
        boolean previousMayBuild = player.abilities.mayBuild;
        // Spectator cannot modify inventories/world. While the controller is
        // still in its entry Spectator mode, expose Survival semantics only for
        // the duration of the vanilla action. If the user changed to Creative,
        // Survival or Adventure, preserve that real game mode and its rules.
        GameType actionGameType = previousGameType == GameType.SPECTATOR
                ? GameType.SURVIVAL : previousGameType;
        try {
            if (actionGameType != previousGameType) {
                gameMode.vanillaInstincts$setGameModeForPlayer(actionGameType);
                player.abilities.mayBuild = true;
            }
            // The hidden controller body follows the mob for reach/container
            // checks; only the body position is proxied, never the target hit.
            syncControllerBody(player, mob);
            return action.get();
        } finally {
            player.abilities.mayBuild = previousMayBuild;
            if (actionGameType != previousGameType) {
                gameMode.vanillaInstincts$setGameModeForPlayer(previousGameType);
            }
        }
    }

    private static void syncControllerBody(EntityPlayerMP player, EntityLiving mob) {
        player.absMoveTo(mob.getX(), mob.getY(), mob.getZ(),
                mob.yRot, mob.xRot);
        player.setDeltaMovement(mob.getMotion());
        player.onGround = mob.onGround;
    }

    private static void dropSelected(EntityPlayerMP player, Session session,
                                     boolean wholeStack) {
        int selected = MathHelper.clamp(player.inventory.selected, 0, 8);
        ItemStack stack = player.inventory.getItem(selected);
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return;
        ItemStack dropped = wholeStack
                ? player.inventory.removeItemNoUpdate(selected)
                : player.inventory.removeItem(selected, 1);
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(dropped)) return;
        withPlayerProxy(player, session.mob, () -> {
            player.drop(dropped, false);
            return Boolean.TRUE;
        });
        PossessionPlayerInventory.syncEquipment(player, session.mob);
        player.inventoryMenu.broadcastChanges();
    }

    private static void swapOffhand(EntityPlayerMP player, Session session) {
        int selected = MathHelper.clamp(player.inventory.selected, 0, 8);
        ItemStack main = player.inventory.getItem(selected);
        ItemStack off = player.inventory.offhand.get(0);
        player.inventory.setItem(selected, off);
        player.inventory.offhand.set(0, main);
        PossessionPlayerInventory.syncEquipment(player, session.mob);
        player.inventoryMenu.broadcastChanges();
    }

    private static void primary(EntityPlayerMP player, Session session) {
        EntityLiving mob = session.mob;
        long gameTime = mob.worldObj.getTotalWorldTime();
        if (gameTime < session.primaryReadyAt) return;

        boolean nativeAbilities =
                VanillaInstinctsServerConfig.possessionAbilitiesEnabled();

        EntityEnderCrystal crystal = PossessionAim.findEndCrystal(mob, PLAYER_INTERACTION_RANGE);
        if (crystal != null) {
            withPlayerProxy(player, mob, () -> {
                player.attack(crystal);
                return Boolean.TRUE;
            });
            mob.swingItem();
            session.primaryReadyAt = gameTime + MELEE_COOLDOWN_TICKS;
            return;
        }

        ItemStack heldItem = player.getHeldItem();
        boolean heldItemAttack = !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(heldItem);
        double targetRange = heldItemAttack
                ? PLAYER_INTERACTION_RANGE
                : (nativeAbilities
                ? VanillaInstinctsServerConfig.possessionRangedRange()
                : PLAYER_INTERACTION_RANGE);
        EntityLivingBase target = PossessionAim.findLiving(mob, player, targetRange);
        if (target == null) return;

        if (heldItemAttack) {
            if (mob.distanceToSqr(target) <= PossessionAim.meleeReachSqr(mob, target)) {
                withPlayerProxy(player, mob, () -> {
                    player.attack(target);
                    return Boolean.TRUE;
                });
                mob.swingItem();
                session.primaryReadyAt = gameTime + MELEE_COOLDOWN_TICKS;
            }
            return;
        }

        if (nativeAbilities && mob instanceof EntityDragon) { EntityDragon dragon = (EntityDragon) (mob); 
            dragon.getPhaseManager().setPhase(PhaseType.CHARGING_PLAYER);
            PhaseChargingPlayer phase = dragon.getPhaseManager()
                    .getPhase(PhaseType.CHARGING_PLAYER);
            phase.setAttackTarget(target.getEyePosition(1.0F));
            session.primaryReadyAt = gameTime + 40L;
            return;
        }

        if (nativeAbilities && mob instanceof IRangedAttackMob
                && !(mob instanceof EntityWitch)
                && !(mob instanceof EntitySkeleton
                && PossessionSkeletonBowController.bowHand(mob) != null)) { IRangedAttackMob ranged = (IRangedAttackMob) (mob); 
            // Directly invoke the species ranged attack. Reopening its whole AI
            // would also reopen navigation/Brain decisions and fight the player.
            ranged.performRangedAttack(target, 1.0F);
            mob.swingItem();
            session.primaryReadyAt = gameTime + RANGED_COOLDOWN_TICKS;
            return;
        }

        if (nativeAbilities && mob.getAttribute(SharedMonsterAttributes.ATTACK_DAMAGE) != null
                && mob.distanceToSqr(target) <= PossessionAim.meleeReachSqr(mob, target)) {
            if (mob.attackEntityAsMob(target)) {
                mob.swingItem();
                player.getFoodData().addExhaustion(0.10F);
                session.primaryReadyAt = gameTime + MELEE_COOLDOWN_TICKS;
            }
            return;
        }

        if (mob.distanceToSqr(target) <= PossessionAim.meleeReachSqr(mob, target)) {
            // Passive/utility mobs do not implement an offensive EntityLiving attack.
            // Route their manual left click through the controller's vanilla
            // EntityPlayer attack instead. This gives villagers, animals and similar
            // mobs a normal close-range hit while retaining held-item combat.
            withPlayerProxy(player, mob, () -> {
                player.attack(target);
                return Boolean.TRUE;
            });
            mob.swingItem();
            session.primaryReadyAt = gameTime + MELEE_COOLDOWN_TICKS;
        }
    }

    private static void beginSkeletonBow(Session session,
                                         EnumHand hand) {
        if (!(session.mob instanceof EntitySkeleton)) return; EntitySkeleton skeleton = (EntitySkeleton) (session.mob);
        session.bowDrawing = true;
        session.bowHand = hand;
        session.bowStartedAt = skeleton.worldObj.getTotalWorldTime();
        PossessionSkeletonBowController.begin(skeleton, hand);
    }

    private static void cancelSkeletonBow(Session session) {
        if (!session.bowDrawing) return;
        if (session.mob instanceof EntitySkeleton) { EntitySkeleton skeleton = (EntitySkeleton) (session.mob); 
            PossessionSkeletonBowController.cancel(skeleton);
        }
        session.bowDrawing = false;
        session.bowHand = null;
        session.bowStartedAt = 0L;
    }

    private static void releaseSkeletonBow(EntityPlayerMP player,
                                           Session session) {
        if (!(session.mob instanceof EntitySkeleton)
                || !session.bowDrawing || session.bowHand == null) {
            cancelSkeletonBow(session);
            return;
        } EntitySkeleton skeleton = (EntitySkeleton) (session.mob);
        EnumHand hand = session.bowHand;
        long startedAt = session.bowStartedAt;
        session.bowDrawing = false;
        session.bowHand = null;
        session.bowStartedAt = 0L;
        PossessionSkeletonBowController.release(skeleton, hand, startedAt);
        PossessionPlayerInventory.syncEquipment(player, skeleton);
        player.inventoryMenu.broadcastChanges();
    }

    private static void secondary(EntityPlayerMP player, Session session) {
        EntityLiving mob = session.mob;
        long gameTime = mob.worldObj.getTotalWorldTime();
        if (gameTime < session.secondaryReadyAt) return;

        if (mob instanceof EntityDragon) { EntityDragon dragon = (EntityDragon) (mob); 
            EntityLivingBase target = PossessionAim.findLiving(dragon, player,
                    VanillaInstinctsServerConfig.possessionRangedRange());
            if (target == null) return;
            dragon.getPhaseManager().setPhase(PhaseType.STRAFE_PLAYER);
            PhaseStrafePlayer phase = dragon.getPhaseManager()
                    .getPhase(PhaseType.STRAFE_PLAYER);
            phase.setAttackTarget(target);
            session.secondaryReadyAt = gameTime + 120L;
            return;
        }

        if (mob instanceof EntityWitch) { EntityWitch witch = (EntityWitch) (mob); 
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(player.getHeldItem())) return;
            boolean thrown = withPlayerProxy(player, witch,
                    () -> PossessionWitchPotionController.throwRandomSplash(
                            player, witch));
            if (thrown) session.secondaryReadyAt = gameTime + 20L;
            return;
        }

        if (mob instanceof EntityEnderman) { EntityEnderman enderman = (EntityEnderman) (mob); 
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(player.getHeldItem())) return;
            if (teleportEndermanToAim(enderman)) {
                session.secondaryReadyAt = gameTime
                        + ENDERMAN_TELEPORT_COOLDOWN_TICKS;
            }
            return;
        }

        // No generic AI pulse here: unsupported secondary abilities are a no-op
        // rather than allowing the mob to steal movement/navigation control.
    }

    private static boolean teleportEndermanToAim(EntityEnderman enderman) {
        MovingObjectPosition aimed = enderman.pick(ENDERMAN_TELEPORT_RANGE, 1.0F, false);
        if (!(aimed instanceof BlockRayTraceResult)
                || aimed.getType() != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        } BlockRayTraceResult blockHit = (BlockRayTraceResult) (aimed);

        WorldServer level = (WorldServer) enderman.worldObj;
        Vec3 origin = fr.vanillainstincts.compat.Minecraft17Compat.position(enderman);
        BlockPos preferred = blockHit.getBlockPos()
                .relative(blockHit.getDirection());
        AxisAlignedBB original = enderman.getBoundingBox();

        // Prefer the exact adjacent space selected by the crosshair, then look
        // a few blocks down for a safe floor. This makes top-face clicks land
        // on the block while side-face clicks can land beside walls/cliffs.
        for (int drop = 0; drop <= 6; drop++) {
            BlockPos feet = preferred.below(drop);
            if (!fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, feet.down()).getBlock().getMaterial().blocksMotion()) continue;
            Vec3 target = new Vec3(feet.getX() + 0.5D, feet.getY(),
                    feet.getZ() + 0.5D);
            if (target.distanceToSqr(origin)
                    > ENDERMAN_TELEPORT_RANGE * ENDERMAN_TELEPORT_RANGE) {
                continue;
            }
            Vec3 delta = target.subtract(origin);
            if (!fr.vanillainstincts.compat.Minecraft112Compat.noCollision(level, enderman, original.move(delta))) continue;
            return performManualEndermanTeleport(enderman, target);
        }
        return false;
    }

    private static boolean performManualEndermanTeleport(EntityEnderman enderman,
                                                           Vec3 target) {
        UUID previous = MANUAL_ENDERMAN_TELEPORT.get();
        MANUAL_ENDERMAN_TELEPORT.set(enderman.getUniqueID());
        try {
            return enderman.randomTeleport(target.xCoord, target.yCoord, target.zCoord, true);
        } finally {
            if (previous == null) {
                MANUAL_ENDERMAN_TELEPORT.remove();
            } else {
                MANUAL_ENDERMAN_TELEPORT.set(previous);
            }
        }
    }

    private static PhaseType<?> currentDragonPhase(EntityLiving mob) {
        if (!(mob instanceof EntityDragon)) return null; EntityDragon dragon = (EntityDragon) (mob);
        IPhase current = dragon.getPhaseManager().getCurrentPhase();
        return current == null ? null : current.getPhase();
    }

    private static GoalSelector goalSelector(EntityLiving mob) {
        return ((MobGoalSelectorAccessor) mob)
                .vanillaInstincts$getGoalSelector();
    }

    private static GoalSelector targetSelector(EntityLiving mob) {
        return ((MobGoalSelectorAccessor) mob)
                .vanillaInstincts$getTargetSelector();
    }

    private static EnumSet<Goal.Flag> copyDisabledFlags(GoalSelector selector) {
        EnumSet<Goal.Flag> flags = ((GoalSelectorDisabledFlagsAccessor) selector)
                .vanillaInstincts$getDisabledFlags();
        return flags.isEmpty() ? EnumSet.noneOf(Goal.Flag.class)
                : EnumSet.copyOf(flags);
    }

    private static void restoreDisabledFlags(GoalSelector selector,
                                             EnumSet<Goal.Flag> saved) {
        EnumSet<Goal.Flag> flags = ((GoalSelectorDisabledFlagsAccessor) selector)
                .vanillaInstincts$getDisabledFlags();
        flags.clear();
        flags.addAll(saved);
    }

    private static void suppressGoals(Session session, boolean suppressed) {
        if (suppressed == session.goalsSuppressed) return;
        if (suppressed) {
            EnumSet<Goal.Flag> all = EnumSet.allOf(Goal.Flag.class);
            EnumSet<Goal.Flag> goalFlags = ((GoalSelectorDisabledFlagsAccessor)
                    goalSelector(session.mob)).vanillaInstincts$getDisabledFlags();
            EnumSet<Goal.Flag> targetFlags = ((GoalSelectorDisabledFlagsAccessor)
                    targetSelector(session.mob)).vanillaInstincts$getDisabledFlags();
            goalFlags.addAll(all);
            targetFlags.addAll(all);
        } else {
            restoreDisabledFlags(goalSelector(session.mob),
                    session.previousGoalDisabled);
            restoreDisabledFlags(targetSelector(session.mob),
                    session.previousTargetDisabled);
        }
        session.goalsSuppressed = suppressed;
    }

    private static float axis(int flags, int positive, int negative) {
        return (has(flags, positive) ? 1.0F : 0.0F)
                - (has(flags, negative) ? 1.0F : 0.0F);
    }

    private static boolean has(int flags, int flag) {
        return (flags & flag) != 0;
    }

    private static void killController(EntityPlayerMP player) {
        if (player == null || !player.isEntityAlive()) return;
        // Minecraft 1.18 does not expose DamageSources#genericKill yet.
        // Kill the controller directly so possession death also works in Creative.
        player.setInvulnerable(false);
        player.kill();
    }

    private static void message(EntityPlayerMP player, String key) {
        player.displayClientMessage(new net.minecraft.util.text.TextComponentTranslation(key), true);
    }

    private enum StopReason {
        PLAYER_REQUEST(true, "message.vanillainstincts.possession.stopped"),
        RETURNED_TO_SPECTATOR(true,
                "message.vanillainstincts.possession.returned_to_spectator"),
        INVALID_STATE(true, "message.vanillainstincts.possession.invalid_state"),
        MOB_REMOVED(true, "message.vanillainstincts.possession.mob_removed"),
        MOB_DIED(false, ""),
        LEVEL_UNLOAD(false, ""),
        LOGOUT(false, "");

        private final boolean visible;
        private final String translationKey;

        StopReason(boolean visible, String translationKey) {
            this.visible = visible;
            this.translationKey = translationKey;
        }
    }

    private static final class Session {
        private EntityLiving mob;
        private final boolean previousNoAi;
        private final boolean previousNoGravity;
        private final boolean previousMobInvulnerable;
        private final boolean previousAggressive;
        private final EntityLivingBase previousTarget;
        private final EnumSet<Goal.Flag> previousGoalDisabled;
        private final EnumSet<Goal.Flag> previousTargetDisabled;
        private final PhaseType<?> previousDragonPhase;
        private int flags;
        private float yaw;
        private float pitch;
        private boolean jumpHeld;
        private boolean creeperFuse;
        private long primaryReadyAt;
        private long secondaryReadyAt;
        private boolean goalsSuppressed;
        private boolean attackHeld;
        private boolean useHeld;
        private boolean useInteractionConsumed;
        private BlockPos breakPos;
        private float breakProgress;
        private long nextMiningSwingAt;
        private long nextInventoryPersistAt;
        private PossessionPlayerInventory.Snapshot inventorySnapshot;
        private boolean previousPlayerInvisible;
        private boolean previousPlayerInvulnerable;
        private float previousPlayerHealth;
        private float previousPlayerAbsorption;
        private Vec3 lastFoodPosition;
        private boolean lastFoodSprint;
        private int foodTickTimer;
        private ConsumableProxySnapshot consumableSnapshot;
        private boolean dimensionTransferPending;
        private boolean dimensionTransferReady;
        private int dimensionTransferGraceTicks;
        private PossessionPlayerInventory.Snapshot dimensionInventorySnapshot;
        private GameType lastObservedGameType;
        private boolean bowDrawing;
        private EnumHand bowHand;
        private long bowStartedAt;

        private Session(EntityLiving mob, boolean previousNoAi,
                        boolean previousNoGravity,
                        boolean previousMobInvulnerable,
                        boolean previousAggressive, EntityLivingBase previousTarget,
                        float yaw, float pitch,
                        EnumSet<Goal.Flag> previousGoalDisabled,
                        EnumSet<Goal.Flag> previousTargetDisabled,
                        PhaseType<?> previousDragonPhase) {
            this.mob = mob;
            this.previousNoAi = previousNoAi;
            this.previousNoGravity = previousNoGravity;
            this.previousMobInvulnerable = previousMobInvulnerable;
            this.previousAggressive = previousAggressive;
            this.previousTarget = previousTarget;
            this.previousGoalDisabled = previousGoalDisabled;
            this.previousTargetDisabled = previousTargetDisabled;
            this.previousDragonPhase = previousDragonPhase;
            this.yaw = yaw;
            this.pitch = pitch;
            this.lastObservedGameType = GameType.SURVIVAL;
            this.lastFoodPosition = fr.vanillainstincts.compat.Minecraft17Compat.position(mob);
        }
    }

    private static class ConsumableProxySnapshot {
        private final float health;
        private final float absorption;
        private final Map<Effect, PotionEffect> effects;

        public ConsumableProxySnapshot(float health, float absorption, Map<Effect, PotionEffect> effects) {
            this.health = health;
            this.absorption = absorption;
            this.effects = effects;
        }

        public float health() { return this.health; }

        public float absorption() { return this.absorption; }

        public Map<Effect, PotionEffect> effects() { return this.effects; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ConsumableProxySnapshot)) return false;
            ConsumableProxySnapshot that = (ConsumableProxySnapshot) other;
            return Float.compare(this.health, that.health) == 0 && Float.compare(this.absorption, that.absorption) == 0 && java.util.Objects.equals(this.effects, that.effects);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.health, this.absorption, this.effects); }

        @Override
        public String toString() {
            return "ConsumableProxySnapshot[" + "health=" + this.health + ", " + "absorption=" + this.absorption + ", " + "effects=" + this.effects + "]";
        }

        private static ConsumableProxySnapshot capture(EntityPlayerMP player) {
            Map<Effect, PotionEffect> effects =
                    new LinkedHashMap<>();
            for (PotionEffect effect : player.getActiveEffects()) {
                effects.put(effect.getEffect(), new PotionEffect(effect));
            }
            return new ConsumableProxySnapshot(player.getHealth(),
                    player.getAbsorptionAmount(), effects);
        }

        private void restore(EntityPlayerMP player) {
            player.removeAllEffects();
            for (PotionEffect effect : effects.values()) {
                player.addEffect(new PotionEffect(effect));
            }
            player.setHealth(Math.min(player.getMaxHealth(),
                    Math.max(0.1F, health)));
            player.setAbsorptionAmount(Math.max(0.0F, absorption));
        }
    }
}

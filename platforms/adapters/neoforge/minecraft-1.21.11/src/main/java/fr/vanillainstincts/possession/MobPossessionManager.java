package fr.vanillainstincts.possession;
import fr.vanillainstincts.config.VanillaInstinctsServerConfig;
import fr.vanillainstincts.network.PossessionActionPayload;
import fr.vanillainstincts.network.PossessionInputPayload;
import fr.vanillainstincts.network.PossessionStatePayload;
import fr.vanillainstincts.mixin.GoalSelectorDisabledFlagsAccessor;
import fr.vanillainstincts.mixin.MobGoalSelectorAccessor;
import fr.vanillainstincts.mixin.ServerPlayerGameModeAccessor;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonChargePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonStrafePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.network.PacketDistributor;
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
    public static int toggleFromSpectatorCamera(ServerPlayer player) {
        if (player == null) return 0;
        if (BY_PLAYER.containsKey(player.getUUID())) {
            // /vanillainstincts control is a true toggle. Re-running it while
            // already possessing a mob releases the mob, restores the real
            // player inventory/body, returns the camera to the player and puts
            // that player back into ordinary Spectator mode.
            stop(player, StopReason.PLAYER_REQUEST);
            if (!player.isSpectator()) {
                player.setGameMode(GameType.SPECTATOR);
            }
            return 1;
        }
        if (!VanillaInstinctsServerConfig.possessionEnabled()) {
            message(player, "message.vanillainstincts.possession.disabled");
            return 0;
        }
        if (!player.isSpectator()) {
            message(player, "message.vanillainstincts.possession.spectator_only");
            return 0;
        }
        Entity camera = player.getCamera();
        if (!(camera instanceof Mob mob) || camera == player
                || !mob.isAlive() || mob.isRemoved()) {
            message(player, "message.vanillainstincts.possession.camera_required");
            return 0;
        }
        return start(player, mob) ? 1 : 0;
    }
    public static void acceptInput(ServerPlayer player,
                                   PossessionInputPayload payload) {
        Session session = player == null ? null : BY_PLAYER.get(player.getUUID());
        if (session == null || payload == null) return;
        // Never trust arbitrary client bits or rotations. The packet carries
        // intent only; movement remains fully computed on the server.
        session.flags = payload.flags() & INPUT_MASK;
        session.yaw = Mth.wrapDegrees(payload.yaw());
        session.pitch = Mth.clamp(payload.pitch(), -90.0F, 90.0F);
    }
    public static void action(ServerPlayer player, int action) {
        Session session = player == null ? null : BY_PLAYER.get(player.getUUID());
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
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            Mob mob = session.mob;
            if (player != null && mob != null && mob.isAlive()
                    && !mob.isRemoved() && player.level() != mob.level()) {
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
                        && mob != null && mob.isAlive() && !mob.isRemoved()) {
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
                    if (mob != null) BY_MOB.remove(mob.getUUID());
                    iterator.remove();
                    if (player != null) {
                        resetCameraAndNotify(player, StopReason.MOB_REMOVED);
                    }
                    continue;
                }
            }
            if (player != null && mob != null && !mob.isAlive()) {
                restoreControllerInventory(player, session);
                restoreControllerBodyState(player, session);
                restoreMob(session);
                BY_MOB.remove(mob.getUUID());
                iterator.remove();
                resetCameraAndNotify(player, StopReason.MOB_DIED);
                killController(player);
                continue;
            }
            if (player == null || !valid(player, mob)) {
                if (player != null) {
                    restoreControllerInventory(player, session);
                    restoreControllerBodyState(player, session);
                }
                restoreMob(session);
                if (mob != null) BY_MOB.remove(mob.getUUID());
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
     * Run after normal player ticks so FoodData has already converted exhaustion
     * into saturation/food-level changes for this server tick.
     */
    public static void tickFood(MinecraftServer server) {
        if (server == null || BY_PLAYER.isEmpty()) return;
        for (Map.Entry<UUID, Session> entry : BY_PLAYER.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || !valid(player, session.mob)) continue;
            tickPossessionFood(player, session, player.isCreative());
        }
    }
    public static boolean isPossessed(Mob mob) {
        return mob != null && BY_MOB.containsKey(mob.getUUID());
    }
    /**
     * Vanilla Endermen may teleport from their own hurt/environment logic even
     * while GoalSelector and Brain are suppressed. During possession only the
     * explicit right-click teleport is allowed through randomTeleport().
     */
    public static boolean mayRandomTeleport(EnderMan enderman) {
        if (enderman == null || !isPossessed(enderman)) return true;
        UUID allowed = MANUAL_ENDERMAN_TELEPORT.get();
        return enderman.getUUID().equals(allowed);
    }
    /** True for the whole possession: the mob Brain never competes with input. */
    public static boolean suppressAutonomousBrain(Mob mob) {
        return isPossessed(mob);
    }
    public static boolean isPossessing(Player player, Mob mob) {
        if (player == null || mob == null) return false;
        Session session = BY_PLAYER.get(player.getUUID());
        return session != null && session.mob == mob;
    }
    public static Mob possessedBy(ServerPlayer player) {
        Session session = player == null ? null : BY_PLAYER.get(player.getUUID());
        return session == null ? null : session.mob;
    }
    /**
     * Ender pearls are launched by the hidden ServerPlayer proxy because item
     * use is delegated to vanilla player mechanics. Vanilla would therefore
     * teleport that hidden proxy on impact and leave the controlled mob behind.
     * Redirect the pearl destination to the possessed body instead while
     * preserving the normal pearl fall reset and damage on that body.
     */
    public static void onEnderPearlTeleport(
            EntityTeleportEvent.EnderPearl event) {
        if (event == null) return;
        ServerPlayer player = event.getPlayer();
        Session session = player == null ? null : BY_PLAYER.get(player.getUUID());
        if (session == null || !valid(player, session.mob)) return;
        if (!(event.getPearlEntity().level() instanceof ServerLevel targetLevel)) {
            return;
        }
        Mob mob = session.mob;
        event.setCanceled(true);
        boolean teleported = mob.teleportTo(targetLevel,
                event.getTargetX(), event.getTargetY(), event.getTargetZ(), Set.of(),
                mob.getYRot(), mob.getXRot(), false);
        if (!teleported) return;
        mob.resetFallDistance();
        if (event.getAttackDamage() > 0.0F) {
            mob.hurtServer(targetLevel, targetLevel.damageSources().fall(),
                    event.getAttackDamage());
        }
        if (!mob.isAlive() || mob.isRemoved()) return;
        // Keep the invisible controller in the same dimension/position so the
        // next possession tick cannot snap the pearl teleport back. Cross-
        // dimension pearls are handled as well as ordinary same-level throws.
        if (player.level() != targetLevel) {
            player.teleportTo(targetLevel, mob.getX(), mob.getY(), mob.getZ(), Set.of(),
                    mob.getYRot(), mob.getXRot(), false);
        }
        syncControllerBody(player, mob);
        if (player.getCamera() != mob) {
            player.setCamera(mob);
        }
    }
    /**
     * Capture the hidden controller state before a vanilla consumable starts.
     * FoodData deliberately remains live; health/effects belong to the possessed
     * body and are restored on the proxy after the item finishes.
     */
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = BY_PLAYER.get(player.getUUID());
        if (session == null || !valid(player, session.mob)) return;
        session.consumableSnapshot = ConsumableProxySnapshot.capture(player);
    }
    /**
     * Let vanilla consume the real inventory stack and update the controller's
     * FoodData, then replay the finished item once on the controlled LivingEntity
     * so potions, enchanted apples, milk and other consumable body effects apply
     * to the mob. The proxy's own health/effects are restored immediately.
     */
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = BY_PLAYER.get(player.getUUID());
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
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = BY_PLAYER.get(player.getUUID());
        if (session != null) session.consumableSnapshot = null;
    }
    /**
     * Possession starts from Spectator, but once established it survives normal
     * Survival/Creative/Adventure changes. Switching back to Spectator releases
     * the controlled mob and restores the player body.
     */
    public static void onPlayerGameModeChange(ServerPlayer player,
                                               GameType current,
                                               GameType next) {
        if (player == null || current == null || next == null) return;
        Session session = BY_PLAYER.get(player.getUUID());
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
    public static void onPlayerLogin(ServerPlayer player) {
        if (PossessionPlayerInventory.recoverStaleBackup(player)) {
            player.displayClientMessage(Component.literal(
                    "Vanilla Instincts: restored inventory after an interrupted possession."),
                    false);
        }
    }
    public static void onPlayerLogout(ServerPlayer player) {
        stop(player, StopReason.LOGOUT);
    }
    /**
     * A possessed mob leaving a level may be changing dimension rather than
     * despawning. Keep the session alive briefly and let onMobJoin rebind the
     * destination-world instance carrying the possession marker.
     */
    public static void onMobLeave(Mob mob) {
        if (mob == null) return;
        UUID playerId = BY_MOB.get(mob.getUUID());
        if (playerId == null) return;
        Session session = BY_PLAYER.get(playerId);
        if (session == null || session.mob != mob) return;
        MinecraftServer server = mob.level().getServer();
        ServerPlayer player = server == null ? null
                : server.getPlayerList().getPlayer(playerId);
        if (player != null && session.dimensionInventorySnapshot == null) {
            session.dimensionInventorySnapshot =
                    PossessionPlayerInventory.captureTemporary(player);
            PossessionPlayerInventory.persistMobInventory(player, mob);
        }
        boolean explodedCreeper = mob instanceof Creeper && session.creeperFuse
                && mob.getRemovalReason() == Entity.RemovalReason.DISCARDED;
        if (mob.getHealth() <= 0.0F || explodedCreeper) {
            BY_MOB.remove(mob.getUUID());
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
    public static void onMobJoin(Mob mob) {
        if (mob == null || mob.level().isClientSide()) return;
        if (!fr.vanillainstincts.persistence.NbtCompat.hasUuid(mob.getPersistentData(), POSSESSION_CONTROLLER_KEY)) return;
        UUID playerId = fr.vanillainstincts.persistence.NbtCompat.getUuid(mob.getPersistentData(), POSSESSION_CONTROLLER_KEY);
        Session session = BY_PLAYER.get(playerId);
        if (session == null) {
            // A stale marker must never create a possession after a restart.
            mob.getPersistentData().remove(POSSESSION_CONTROLLER_KEY);
            return;
        }
        Mob previous = session.mob;
        if (previous != mob) {
            if (previous != null) BY_MOB.remove(previous.getUUID());
            session.mob = mob;
            BY_MOB.put(mob.getUUID(), playerId);
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
    private static void completeDimensionTransfer(ServerPlayer player,
                                                  Session session) {
        Mob mob = session.mob;
        if (!(mob.level() instanceof ServerLevel targetLevel)) return;
        session.dimensionTransferPending = false;
        session.dimensionTransferReady = false;
        session.dimensionTransferGraceTicks = 0;
        fr.vanillainstincts.persistence.NbtCompat.putUuid(mob.getPersistentData(), POSSESSION_CONTROLLER_KEY, player.getUUID());
        session.goalsSuppressed = false;
        mob.getNavigation().stop();
        mob.stopInPlace();
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.setNoAi(false);
        suppressGoals(session, true);
        if (session.dimensionInventorySnapshot == null) {
            session.dimensionInventorySnapshot =
                    PossessionPlayerInventory.captureTemporary(player);
        }
        if (player.level() != targetLevel) {
            player.teleportTo(targetLevel, mob.getX(), mob.getY(), mob.getZ(), Set.of(),
                    mob.getYRot(), mob.getXRot(), false);
        }
        PossessionPlayerInventory.restoreTemporary(player,
                session.dimensionInventorySnapshot);
        session.dimensionInventorySnapshot = null;
        player.noPhysics = true;
        player.setInvisible(true);
        player.setInvulnerable(true);
        player.setPortalCooldown();
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
        PacketDistributor.sendToPlayer(player,
                new PossessionStatePayload(true, mob.getId()));
    }
    public static void clearLevel(ServerLevel level) {
        if (level == null || BY_PLAYER.isEmpty()) return;
        Iterator<Map.Entry<UUID, Session>> iterator = BY_PLAYER.entrySet()
                .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            Session session = entry.getValue();
            if (session.mob.level() != level) continue;
            BY_MOB.remove(session.mob.getUUID());
            ServerPlayer player = level.getServer().getPlayerList()
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
    private static boolean start(ServerPlayer player, Mob mob) {
        if (!VanillaInstinctsServerConfig.possessionEnabled()) {
            message(player, "message.vanillainstincts.possession.disabled");
            return false;
        }
        if (!player.isSpectator()) {
            message(player, "message.vanillainstincts.possession.spectator_only");
            return false;
        }
        if (mob == null || player.getCamera() != mob || !mob.isAlive()
                || mob.isRemoved() || mob.level() != player.level()) {
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
        if (BY_MOB.containsKey(mob.getUUID())) {
            message(player, "message.vanillainstincts.possession.already_controlled");
            return false;
        }
        Session session = new Session(mob, mob.isNoAi(), mob.isNoGravity(),
                mob.isInvulnerable(), mob.isAggressive(), mob.getTarget(),
                mob.getYRot(), mob.getXRot(),
                copyDisabledFlags(goalSelector(mob)),
                copyDisabledFlags(targetSelector(mob)),
                currentDragonPhase(mob));
        BY_PLAYER.put(player.getUUID(), session);
        BY_MOB.put(mob.getUUID(), player.getUUID());
        fr.vanillainstincts.persistence.NbtCompat.putUuid(mob.getPersistentData(), POSSESSION_CONTROLLER_KEY, player.getUUID());
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
        mob.getNavigation().stop();
        mob.stopInPlace();
        mob.setTarget(null);
        mob.setAggressive(false);
        // Strict ownership: ordinary mobs never get a temporary AI pulse while
        // a player is driving them.  Environmental/entity ticks still run, but
        // GoalSelector and Brain cannot fight the controller for movement.
        mob.setNoAi(false);
        suppressGoals(session, true);
        applyPossessionGravity(session, true);
        player.setCamera(mob);
        PacketDistributor.sendToPlayer(player,
                new PossessionStatePayload(true, mob.getId()));
        player.displayClientMessage(Component.translatable(
                "message.vanillainstincts.possession.started",
                mob.getDisplayName()), true);
        return true;
    }

    private static void stop(ServerPlayer player, StopReason reason) {
        if (player == null) return;
        Session session = BY_PLAYER.remove(player.getUUID());
        if (session == null) {
            if (reason == StopReason.PLAYER_REQUEST) {
                PacketDistributor.sendToPlayer(player,
                        new PossessionStatePayload(false, -1));
            }
            return;
        }
        BY_MOB.remove(session.mob.getUUID());
        restoreControllerInventory(player, session);
        restoreControllerBodyState(player, session);
        restoreMob(session);
        // Every release path returns the camera to the real player. The command
        // additionally switches the player to Spectator after this method;
        // /gamemode spectator performs that game-mode change itself immediately
        // after the NeoForge pre-change event returns.
        resetCameraAndNotify(player, reason);
    }

    private static void notifyStopped(ServerPlayer player, StopReason reason) {
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        PacketDistributor.sendToPlayer(player,
                new PossessionStatePayload(false, -1));
        if (reason.visible) {
            player.displayClientMessage(Component.translatable(
                    reason.translationKey), true);
        }
    }

    private static void resetCameraAndNotify(ServerPlayer player,
                                             StopReason reason) {
        // Deactivate the client possession guard BEFORE vanilla's camera packet
        // arrives. Otherwise the client-side keep-camera mixin can correctly
        // reject the reset because it still believes the session is active.
        notifyStopped(player, reason);
        if (player.getCamera() != player) player.setCamera(player);
    }

    private static void restoreControllerInventory(ServerPlayer player,
                                                   Session session) {
        if (session.inventorySnapshot == null) return;
        PossessionPlayerInventory.saveMobInventory(player, session.mob);
        PossessionPlayerInventory.restore(player, session.inventorySnapshot);
        session.inventorySnapshot = null;
    }

    private static void restoreControllerBodyState(ServerPlayer player,
                                                   Session session) {
        player.setInvisible(session.previousPlayerInvisible);
        player.setInvulnerable(session.previousPlayerInvulnerable);
        player.setHealth(Math.min(player.getMaxHealth(),
                Math.max(0.1F, session.previousPlayerHealth)));
        player.setAbsorptionAmount(Math.max(0.0F,
                session.previousPlayerAbsorption));
        // Spectator naturally uses noPhysics; normal modes should regain their
        // ordinary collision body after possession ends.
        player.noPhysics = player.isSpectator();
    }

    private static void restoreMob(Session session) {
        Mob mob = session.mob;
        if (mob == null) return;
        mob.getPersistentData().remove(POSSESSION_CONTROLLER_KEY);
        if (mob.isRemoved()) return;
        if (session.bowDrawing && mob instanceof AbstractSkeleton skeleton) {
            skeleton.stopUsingItem();
            session.bowDrawing = false;
            session.bowHand = null;
        }
        if (mob instanceof Creeper creeper) creeper.setSwellDir(-1);
        mob.setXxa(0.0F);
        mob.setYya(0.0F);
        mob.setZza(0.0F);
        mob.setSprinting(false);
        mob.setShiftKeyDown(false);
        if (mob instanceof EnderDragon dragon
                && session.previousDragonPhase != null) {
            dragon.getPhaseManager().setPhase(session.previousDragonPhase);
        }
        restoreDisabledFlags(goalSelector(mob), session.previousGoalDisabled);
        restoreDisabledFlags(targetSelector(mob), session.previousTargetDisabled);
        mob.setNoGravity(session.previousNoGravity);
        mob.setInvulnerable(session.previousMobInvulnerable);
        mob.setNoAi(session.previousNoAi);
        mob.setAggressive(session.previousAggressive);
        if (session.previousTarget != null && session.previousTarget.isAlive()
                && session.previousTarget.level() == mob.level()) {
            mob.setTarget(session.previousTarget);
        } else {
            mob.setTarget(null);
        }
    }

    private static boolean valid(ServerPlayer player, Mob mob) {
        // Spectator is required only to ENTER possession. Once controlling the
        // mob, changing to Survival/Creative/Adventure must not tear down the
        // session; only mob/player death, removal, dimension mismatch or an
        // explicit transition back to Spectator ends it.
        return player.isAlive()
                && mob != null && mob.isAlive() && !mob.isRemoved()
                && player.level() == mob.level();
    }

    private static void applyControl(ServerPlayer player, Session session) {
        Mob mob = session.mob;
        // Game-mode changes can rewrite player abilities/body flags. The hidden
        // controller stays a non-physical, invulnerable proxy until release.
        player.noPhysics = true;
        player.setInvisible(true);
        player.setInvulnerable(true);
        // Only the possessed mob is allowed to drive portal transitions. The
        // proxy follows it explicitly after the mob joins the destination.
        player.setPortalCooldown();
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
        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.setAggressive(false);
        PossessionPlayerInventory.syncEquipment(player, mob);

        mob.setYRot(session.yaw);
        mob.setXRot(session.pitch);
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
        // Mob implementations report canSprint() == false even though their
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
                && mob.isInWater();
        if (creativeControl) {
            steerCreativeFlight(mob, forward, strafe, jump, descend, sprint);
        } else if (movement == VanillaMobPossessionCatalog.Movement.DRAGON
                && mob instanceof EnderDragon dragon) {
            steerDragon(dragon, forward, strafe, jump, descend, sprint);
        } else if (movement == VanillaMobPossessionCatalog.Movement.STATIONARY) {
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
        } else if (free3d) {
            steerThreeDimensional(mob, forward, strafe, jump, descend, sprint);
        } else if (movement == VanillaMobPossessionCatalog.Movement.AQUATIC) {
            // Pure swimmers retain their real out-of-water physics.
            mob.getNavigation().stop();
        } else {
            steerGround(player, session, forward, strafe, jump, sprint, sneak,
                    movement);
        }

        pickupNearbyItems(player, session);
        handleContinuousActions(player, session);
        long gameTime = mob.level().getGameTime();
        if (gameTime >= session.nextInventoryPersistAt) {
            PossessionPlayerInventory.persistMobInventory(player, mob);
            session.nextInventoryPersistAt = gameTime + 20L;
        }
        session.lastFoodSprint = sprint;
        session.jumpHeld = jump;
        applyHungerEffectExhaustion(player, session, creativeControl);
    }

    private static void steerCreativeFlight(Mob mob, float forward,
                                            float strafe, boolean ascend,
                                            boolean descend, boolean sprint) {
        // Creative flight is deliberately independent from the mob species:
        // even ground, aquatic and normally stationary mobs can fly while their
        // controller is in Creative, just like a Creative player can.
        Vec3 forwardVector = Vec3.directionFromRotation(0.0F, mob.getYRot());
        Vec3 right = new Vec3(-forwardVector.z, 0.0D, forwardVector.x);
        Vec3 desired = forwardVector.scale(forward)
                .add(right.scale(-strafe))
                .add(0.0D, ascend ? 1.0D : descend ? -1.0D : 0.0D, 0.0D);
        if (desired.lengthSqr() < 1.0E-5D) {
            mob.setDeltaMovement(Vec3.ZERO);
            return;
        }
        double speed = sprint ? 0.45D : 0.25D;
        mob.setDeltaMovement(desired.normalize().scale(speed));
    }

    private static void steerThreeDimensional(Mob mob, float forward,
                                                  float strafe,
                                                  boolean ascend,
                                                  boolean descend,
                                                  boolean sprint) {
        Vec3 look = mob.getLookAngle();
        Vec3 horizontalRight = new Vec3(-look.z, 0.0D, look.x);
        if (horizontalRight.lengthSqr() > 1.0E-6D) {
            horizontalRight = horizontalRight.normalize();
        }
        Vec3 desired = look.scale(forward)
                .add(horizontalRight.scale(-strafe))
                .add(0.0D, ascend ? 1.0D : descend ? -1.0D : 0.0D, 0.0D);
        if (desired.lengthSqr() < 1.0E-5D) {
            // Flying mobs hover; swimmers retain their natural water physics.
            if (VanillaMobPossessionCatalog.movement(mob)
                    == VanillaMobPossessionCatalog.Movement.FLYING) {
                mob.setDeltaMovement(Vec3.ZERO);
            } else {
                Vec3 current = mob.getDeltaMovement();
                mob.setDeltaMovement(current.x * 0.55D, current.y,
                        current.z * 0.55D);
            }
            return;
        }
        desired = desired.normalize();
        double speed = movementSpeed(mob) * (sprint ? 1.30D : 1.0D);
        mob.setDeltaMovement(desired.scale(speed));
    }

    private static void steerGround(ServerPlayer player, Session session,
                                    float forward, float strafe, boolean jump,
                                    boolean sprint, boolean sneak,
                                    VanillaMobPossessionCatalog.Movement movement) {
        Mob mob = session.mob;
        Vec3 forwardVector = Vec3.directionFromRotation(0.0F, mob.getYRot());
        Vec3 right = new Vec3(-forwardVector.z, 0.0D, forwardVector.x);
        Vec3 desired = forwardVector.scale(forward).add(right.scale(-strafe));
        Vec3 current = mob.getDeltaMovement();
        if (desired.lengthSqr() > 1.0E-5D) {
            desired = desired.normalize();
            double multiplier = sprint ? 1.30D : sneak ? 0.30D : 1.0D;
            double speed = movementSpeed(mob) * multiplier;
            double dx = desired.x * speed;
            double dz = desired.z * speed;
            if (sneak && mob.onGround()) {
                Vec3 safe = keepSneakingOnLedge(mob, dx, dz);
                dx = safe.x;
                dz = safe.z;
            }
            mob.setDeltaMovement(dx, current.y, dz);
        } else {
            mob.setDeltaMovement(current.x * 0.55D, current.y,
                    current.z * 0.55D);
        }

        String species = VanillaMobPossessionCatalog.path(mob);
        boolean autoHop = "slime".equals(species) || "magma_cube".equals(species)
                || "rabbit".equals(species) || "frog".equals(species);
        boolean moving = Math.abs(forward) > 0.01F || Math.abs(strafe) > 0.01F;
        if (!sneak && mob.onGround() && ((jump && !session.jumpHeld)
                || (autoHop && moving))) {
            mob.jumpFromGround();
            player.getFoodData().addExhaustion(sprint ? 0.20F : 0.05F);
        } else if (movement == VanillaMobPossessionCatalog.Movement.CLIMBING
                && mob.horizontalCollision && (jump || forward > 0.0F)) {
            Vec3 velocity = mob.getDeltaMovement();
            mob.setDeltaMovement(velocity.x, Math.max(velocity.y, 0.20D),
                    velocity.z);
        }
    }

    private static Vec3 keepSneakingOnLedge(Mob mob, double dx, double dz) {
        AABB box = mob.getBoundingBox();
        double safeX = dx;
        double safeZ = dz;
        // Player-style edge safety: if a proposed horizontal component has no
        // collision support roughly half a block below, suppress that component.
        if (mob.level().noCollision(mob, box.move(dx, -0.55D, 0.0D))) {
            safeX = 0.0D;
        }
        if (mob.level().noCollision(mob, box.move(0.0D, -0.55D, dz))) {
            safeZ = 0.0D;
        }
        if (mob.level().noCollision(mob, box.move(safeX, -0.55D, safeZ))) {
            safeX = 0.0D;
            safeZ = 0.0D;
        }
        return new Vec3(safeX, 0.0D, safeZ);
    }


    /** Apply the same distance-based exhaustion costs used by a player body. */
    private static void applyMovementExhaustion(ServerPlayer player,
                                                Session session,
                                                boolean creativeControl) {
        Vec3 current = session.mob.position();
        Vec3 previous = session.lastFoodPosition;
        session.lastFoodPosition = current;
        if (previous == null || creativeControl) return;

        double dx = current.x - previous.x;
        double dy = current.y - previous.y;
        double dz = current.z - previous.z;
        float exhaustion = 0.0F;
        if (session.mob.isInWater()) {
            exhaustion = (float) (Math.sqrt(dx * dx + dy * dy + dz * dz)
                    * 0.01D);
        } else if (session.lastFoodSprint && session.mob.onGround()) {
            exhaustion = (float) (Math.sqrt(dx * dx + dz * dz) * 0.10D);
        }
        if (exhaustion > 0.0F) {
            player.getFoodData().addExhaustion(exhaustion);
        }
    }

    private static void applyHungerEffectExhaustion(ServerPlayer player,
                                                    Session session,
                                                    boolean creativeControl) {
        if (creativeControl || !session.mob.hasEffect(MobEffects.HUNGER)) return;
        MobEffectInstance hunger = session.mob.getEffect(MobEffects.HUNGER);
        if (hunger != null) {
            player.getFoodData().addExhaustion(
                    0.005F * (hunger.getAmplifier() + 1));
        }
    }

    /**
     * Vanilla-style hunger healing/starvation, but with the possessed mob as the
     * health body and the controller's real FoodData as the hunger state.
     */
    private static void tickPossessionFood(ServerPlayer player, Session session,
                                           boolean creativeControl) {
        if (creativeControl || !(session.mob.level() instanceof ServerLevel level)) {
            session.foodTickTimer = 0;
            return;
        }

        Mob mob = session.mob;
        FoodData food = player.getFoodData();
        int foodLevel = food.getFoodLevel();
        float saturation = food.getSaturationLevel();
        boolean hurt = mob.getHealth() < mob.getMaxHealth();
        boolean naturalRegen = level.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);

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
                Difficulty difficulty = level.getDifficulty();
                float health = mob.getHealth();
                if (health > 10.0F || difficulty == Difficulty.HARD
                        || health > 1.0F && difficulty == Difficulty.NORMAL) {
                    mob.hurtServer(level, level.damageSources().starve(), 1.0F);
                }
                session.foodTickTimer = 0;
            }
        } else {
            session.foodTickTimer = 0;
        }
    }

    private static double movementSpeed(Mob mob) {
        if (mob.getAttribute(Attributes.MOVEMENT_SPEED) == null) return 0.10D;
        return Mth.clamp(mob.getAttributeValue(Attributes.MOVEMENT_SPEED),
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

    private static void steerDragon(EnderDragon dragon, float forward,
                                    float strafe, boolean ascend,
                                    boolean descend, boolean sprint) {
        Vec3 look = dragon.getLookAngle().normalize();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x);
        if (right.lengthSqr() > 1.0E-6D) right = right.normalize();
        Vec3 desired = look.scale(forward)
                .add(right.scale(-strafe))
                .add(0.0D, ascend ? 1.0D : descend ? -1.0D : 0.0D, 0.0D);
        if (desired.lengthSqr() < 1.0E-5D) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
            return;
        }
        desired = desired.normalize();
        double lead = sprint ? 28.0D : 18.0D;
        Vec3 wanted = dragon.position().add(desired.scale(lead));
        dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
        DragonChargePlayerPhase phase = dragon.getPhaseManager()
                .getPhase(EnderDragonPhase.CHARGING_PLAYER);
        phase.setTarget(wanted);
    }

    private static void handleContinuousActions(ServerPlayer player,
                                                Session session) {
        boolean attack = has(session.flags, PossessionInputPayload.ATTACK);
        boolean use = has(session.flags, PossessionInputPayload.USE);

        if (attack) {
            EndCrystal crystal = PossessionAim.findEndCrystal(session.mob,
                    PLAYER_INTERACTION_RANGE);
            LivingEntity aimed = PossessionAim.findLiving(session.mob, player,
                    PLAYER_INTERACTION_RANGE);
            if (crystal != null || aimed != null) {
                resetMining(player, session);
                // Every possessed mob keeps a player-like manual attack path.
                // End Crystals are entities but not LivingEntity instances, so
                // they are selected separately and attacked through Player#attack.
                primary(player, session);
            } else {
                mineLookedAtBlock(player, session);
            }
        } else {
            resetMining(player, session);
        }

        boolean abilities = VanillaInstinctsServerConfig.possessionAbilitiesEnabled();
        boolean emptyMainHand = player.getMainHandItem().isEmpty();
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
                && session.mob instanceof Creeper;
        if (session.mob instanceof Creeper creeper) {
            boolean fuseNow = creeperFuseControl && use;
            creeper.setSwellDir(fuseNow ? 1 : -1);
            session.creeperFuse = fuseNow;
        }

        InteractionHand skeletonBow = PossessionSkeletonBowController.bowHand(session.mob);
        if (session.useInteractionConsumed) {
            if (session.bowDrawing) cancelSkeletonBow(session);
        } else if (use && skeletonBow != null) {
            if (!session.bowDrawing) beginSkeletonBow(session, skeletonBow);
            else session.mob.setAggressive(true);
        } else if (!use && session.useHeld && session.bowDrawing) {
            releaseSkeletonBow(player, session);
        } else if (creeperFuseControl) {
            // Holding empty-hand right click directly drives Creeper swell.
            // Releasing the button above sets the direction back to defusing.
        } else if (use && !session.useHeld) {
            boolean manualMobAbility = abilities && emptyMainHand
                    && (session.mob instanceof EnderMan
                    || session.mob instanceof Witch);
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

    private static void mineLookedAtBlock(ServerPlayer player, Session session) {
        Mob mob = session.mob;
        HitResult hit = mob.pick(PLAYER_INTERACTION_RANGE, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)
                || hit.getType() != HitResult.Type.BLOCK) {
            resetMining(player, session);
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        ServerLevel level = player.level();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            resetMining(player, session);
            return;
        }
        if (!pos.equals(session.breakPos)) {
            resetMining(player, session);
            session.breakPos = pos.immutable();
            session.breakProgress = 0.0F;
        }

        float amount = player.isCreative() ? 1.0F : withPlayerProxy(player, mob,
                () -> state.getDestroyProgress(player, level, pos));
        if (amount <= 0.0F) return;
        session.breakProgress += amount;
        int stage = Mth.clamp((int) (session.breakProgress * 10.0F), 0, 9);
        level.destroyBlockProgress(mob.getId(), pos, stage);
        // Keep the real LivingEntity swing state alive while mining so the
        // possessed arm/item animates instead of remaining frozen.
        long gameTime = level.getGameTime();
        if (gameTime >= session.nextMiningSwingAt) {
            mob.swing(InteractionHand.MAIN_HAND);
            session.nextMiningSwingAt = gameTime + 4L;
        }
        if (session.breakProgress < 1.0F) return;

        boolean destroyed = withPlayerProxy(player, mob,
                () -> player.gameMode.destroyBlock(pos));
        level.destroyBlockProgress(mob.getId(), pos, -1);
        session.breakPos = null;
        session.breakProgress = 0.0F;
        if (destroyed) {
            PossessionPlayerInventory.syncEquipment(player, mob);
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void resetMining(ServerPlayer player, Session session) {
        if (session.breakPos != null && session.mob.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(session.mob.getId(), session.breakPos, -1);
        }
        session.breakPos = null;
        session.breakProgress = 0.0F;
        session.nextMiningSwingAt = 0L;
    }

    /**
     * Player-style item pickup using the possessed mob's collision body.
     * The hidden controller owns the temporary possession inventory, so
     * delegating to ItemEntity.playerTouch preserves vanilla pickup delay,
     * ownership checks, pickup events, sounds, statistics and stack merging.
     */
    private static void pickupNearbyItems(ServerPlayer player, Session session) {
        Mob mob = session.mob;
        if (!(mob.level() instanceof ServerLevel level)) return;
        AABB pickupBox = mob.getBoundingBox().inflate(0.35D, 0.20D, 0.35D);
        boolean changed = false;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                pickupBox, entity -> entity.isAlive() && !entity.getItem().isEmpty())) {
            int before = item.getItem().getCount();
            withPlayerProxy(player, mob, () -> {
                item.playerTouch(player);
                return Boolean.TRUE;
            });
            if (!item.isAlive() || item.isRemoved()
                    || item.getItem().getCount() != before) {
                changed = true;
            }
        }
        if (changed) {
            PossessionPlayerInventory.syncEquipment(player, mob);
            player.inventoryMenu.broadcastChanges();
            PossessionPlayerInventory.persistMobInventory(player, mob);
        }
    }

    private static boolean playerLikeEntityUse(ServerPlayer player, Session session) {
        Mob mob = session.mob;
        InteractionResult result = InteractionResult.PASS;
        LivingEntity entityTarget = PossessionAim.findLiving(mob, player, PLAYER_INTERACTION_RANGE);
        if (entityTarget != null) {
            result = withPlayerProxy(player, mob, () ->
                    player.interactOn(entityTarget, InteractionHand.MAIN_HAND));
            if (result == InteractionResult.PASS) {
                result = withPlayerProxy(player, mob, () ->
                        player.interactOn(entityTarget, InteractionHand.OFF_HAND));
            }
        }
        if (result == InteractionResult.PASS) return false;
        PossessionPlayerInventory.syncEquipment(player, mob);
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    /** Fishing rods need their air-use action even while the camera/body is a mob. */
    private static boolean isFishingRod(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem().equals(Items.FISHING_ROD);
    }

    private static boolean playerLikeUse(ServerPlayer player, Session session) {
        Mob mob = session.mob;
        InteractionResult result = InteractionResult.PASS;
        // A possessed mob is not a Player, but fishing is intrinsically
        // player-owned. Use the hidden controller as the vanilla angler
        // while its position/rotation are proxied to the mob. Trying this
        // before block use makes cast/reel work reliably when aiming at
        // water or another ordinary block. Entity interaction still wins
        // in handleContinuousActions when it actually consumes the click.
        if (isFishingRod(player.getItemInHand(InteractionHand.MAIN_HAND))) {
            result = useInAir(player, mob, InteractionHand.MAIN_HAND);
        } else if (isFishingRod(player.getItemInHand(InteractionHand.OFF_HAND))) {
            result = useInAir(player, mob, InteractionHand.OFF_HAND);
        }
        if (result == InteractionResult.PASS && playerLikeEntityUse(player, session)) return true;
        HitResult hit = mob.pick(PLAYER_INTERACTION_RANGE, 1.0F, false);
        if (result == InteractionResult.PASS && hit instanceof BlockHitResult blockHit
                && hit.getType() == HitResult.Type.BLOCK) {
            result = useOnBlock(player, mob, blockHit, InteractionHand.MAIN_HAND);
            if (result == InteractionResult.PASS) {
                result = useOnBlock(player, mob, blockHit, InteractionHand.OFF_HAND);
            }
        }
        if (result == InteractionResult.PASS) {
            result = useInAir(player, mob, InteractionHand.MAIN_HAND);
            if (result == InteractionResult.PASS) {
                result = useInAir(player, mob, InteractionHand.OFF_HAND);
            }
        }
        PossessionPlayerInventory.syncEquipment(player, mob);
        player.inventoryMenu.broadcastChanges();
        return result != InteractionResult.PASS;
    }

    private static InteractionResult useOnBlock(ServerPlayer player, Mob mob,
                                                 BlockHitResult hit,
                                                 InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return withPlayerProxy(player, mob, () -> player.gameMode.useItemOn(
                player, player.level(), stack, hand, hit));
    }

    private static InteractionResult useInAir(ServerPlayer player, Mob mob,
                                               InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return InteractionResult.PASS;
        return withPlayerProxy(player, mob, () -> player.gameMode.useItem(
                player, player.level(), stack, hand));
    }

    private static <T> T withPlayerProxy(ServerPlayer player, Mob mob,
                                         Supplier<T> action) {
        ServerPlayerGameModeAccessor gameMode =
                (ServerPlayerGameModeAccessor) player.gameMode;
        GameType previousGameType = gameMode.vanillaInstincts$getGameModeForPlayer();
        boolean previousMayBuild = player.getAbilities().mayBuild;
        // Spectator cannot modify inventories/world. While the controller is
        // still in its entry Spectator mode, expose Survival semantics only for
        // the duration of the vanilla action. If the user changed to Creative,
        // Survival or Adventure, preserve that real game mode and its rules.
        GameType actionGameType = previousGameType == GameType.SPECTATOR
                ? GameType.SURVIVAL : previousGameType;
        try {
            if (actionGameType != previousGameType) {
                gameMode.vanillaInstincts$setGameModeForPlayer(actionGameType);
                player.getAbilities().mayBuild = true;
            }
            // The hidden controller body follows the mob for reach/container
            // checks; only the body position is proxied, never the target hit.
            syncControllerBody(player, mob);
            return action.get();
        } finally {
            player.getAbilities().mayBuild = previousMayBuild;
            if (actionGameType != previousGameType) {
                gameMode.vanillaInstincts$setGameModeForPlayer(previousGameType);
            }
        }
    }

    private static void syncControllerBody(ServerPlayer player, Mob mob) {
        player.absSnapTo(mob.getX(), mob.getY(), mob.getZ(),
                mob.getYRot(), mob.getXRot());
        player.setDeltaMovement(mob.getDeltaMovement());
        player.setOnGround(mob.onGround());
    }

    private static void dropSelected(ServerPlayer player, Session session,
                                     boolean wholeStack) {
        int selected = Mth.clamp(player.getInventory().getSelectedSlot(), 0, 8);
        ItemStack stack = player.getInventory().getItem(selected);
        if (stack.isEmpty()) return;
        ItemStack dropped = wholeStack
                ? player.getInventory().removeItemNoUpdate(selected)
                : player.getInventory().removeItem(selected, 1);
        if (dropped.isEmpty()) return;
        withPlayerProxy(player, session.mob, () -> {
            player.drop(dropped, false);
            return Boolean.TRUE;
        });
        PossessionPlayerInventory.syncEquipment(player, session.mob);
        player.inventoryMenu.broadcastChanges();
    }

    private static void swapOffhand(ServerPlayer player, Session session) {
        int selected = Mth.clamp(player.getInventory().getSelectedSlot(), 0, 8);
        ItemStack main = player.getInventory().getItem(selected);
        ItemStack off = player.getOffhandItem();
        player.getInventory().setItem(selected, off);
        player.setItemSlot(EquipmentSlot.OFFHAND, main);
        PossessionPlayerInventory.syncEquipment(player, session.mob);
        player.inventoryMenu.broadcastChanges();
    }

    private static void primary(ServerPlayer player, Session session) {
        Mob mob = session.mob;
        long gameTime = mob.level().getGameTime();
        if (gameTime < session.primaryReadyAt) return;

        boolean nativeAbilities =
                VanillaInstinctsServerConfig.possessionAbilitiesEnabled();

        EndCrystal crystal = PossessionAim.findEndCrystal(mob, PLAYER_INTERACTION_RANGE);
        if (crystal != null) {
            withPlayerProxy(player, mob, () -> {
                player.attack(crystal);
                return Boolean.TRUE;
            });
            mob.swing(InteractionHand.MAIN_HAND);
            session.primaryReadyAt = gameTime + MELEE_COOLDOWN_TICKS;
            return;
        }

        ItemStack heldItem = player.getMainHandItem();
        boolean heldItemAttack = !(heldItem.isEmpty());
        double targetRange = heldItemAttack
                ? PossessionAim.heldAttackRange(player)
                : (nativeAbilities
                ? VanillaInstinctsServerConfig.possessionRangedRange()
                : PLAYER_INTERACTION_RANGE);
        LivingEntity target = PossessionAim.findLiving(mob, player, targetRange);
        if (target == null) return;

        if (heldItemAttack) {
            if (PossessionAim.heldAttackInRange(player, target)) {
                withPlayerProxy(player, mob, () -> {
                    player.attack(target);
                    return Boolean.TRUE;
                });
                mob.swing(InteractionHand.MAIN_HAND);
                session.primaryReadyAt = gameTime + MELEE_COOLDOWN_TICKS;
            }
            return;
        }

        if (nativeAbilities && mob instanceof EnderDragon dragon) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
            DragonChargePlayerPhase phase = dragon.getPhaseManager()
                    .getPhase(EnderDragonPhase.CHARGING_PLAYER);
            phase.setTarget(target.getEyePosition());
            session.primaryReadyAt = gameTime + 40L;
            return;
        }

        if (nativeAbilities && mob instanceof RangedAttackMob ranged
                && !(mob instanceof Witch)
                && !(mob instanceof AbstractSkeleton
                && PossessionSkeletonBowController.bowHand(mob) != null)) {
            // Directly invoke the species ranged attack. Reopening its whole AI
            // would also reopen navigation/Brain decisions and fight the player.
            ranged.performRangedAttack(target, 1.0F);
            mob.swing(InteractionHand.MAIN_HAND);
            session.primaryReadyAt = gameTime + RANGED_COOLDOWN_TICKS;
            return;
        }

        if (nativeAbilities && mob.getAttribute(Attributes.ATTACK_DAMAGE) != null
                && mob.distanceToSqr(target) <= PossessionAim.meleeReachSqr(mob, target)) {
            if (mob.doHurtTarget(player.level(), target)) {
                mob.swing(InteractionHand.MAIN_HAND);
                player.getFoodData().addExhaustion(0.10F);
                session.primaryReadyAt = gameTime + MELEE_COOLDOWN_TICKS;
            }
            return;
        }

        if (mob.distanceToSqr(target) <= PossessionAim.meleeReachSqr(mob, target)) {
            // Passive/utility mobs do not implement an offensive Mob attack.
            // Route their manual left click through the controller's vanilla
            // Player attack instead. This gives villagers, animals and similar
            // mobs a normal close-range hit while retaining held-item combat.
            withPlayerProxy(player, mob, () -> {
                player.attack(target);
                return Boolean.TRUE;
            });
            mob.swing(InteractionHand.MAIN_HAND);
            session.primaryReadyAt = gameTime + MELEE_COOLDOWN_TICKS;
        }
    }

    private static void beginSkeletonBow(Session session,
                                         InteractionHand hand) {
        if (!(session.mob instanceof AbstractSkeleton skeleton)) return;
        session.bowDrawing = true;
        session.bowHand = hand;
        session.bowStartedAt = skeleton.level().getGameTime();
        PossessionSkeletonBowController.begin(skeleton, hand);
    }

    private static void cancelSkeletonBow(Session session) {
        if (!session.bowDrawing) return;
        if (session.mob instanceof AbstractSkeleton skeleton) {
            PossessionSkeletonBowController.cancel(skeleton);
        }
        session.bowDrawing = false;
        session.bowHand = null;
        session.bowStartedAt = 0L;
    }

    private static void releaseSkeletonBow(ServerPlayer player,
                                           Session session) {
        if (!(session.mob instanceof AbstractSkeleton skeleton)
                || !session.bowDrawing || session.bowHand == null) {
            cancelSkeletonBow(session);
            return;
        }
        InteractionHand hand = session.bowHand;
        long startedAt = session.bowStartedAt;
        session.bowDrawing = false;
        session.bowHand = null;
        session.bowStartedAt = 0L;
        PossessionSkeletonBowController.release(skeleton, hand, startedAt);
        PossessionPlayerInventory.syncEquipment(player, skeleton);
        player.inventoryMenu.broadcastChanges();
    }

    private static void secondary(ServerPlayer player, Session session) {
        Mob mob = session.mob;
        long gameTime = mob.level().getGameTime();
        if (gameTime < session.secondaryReadyAt) return;

        if (mob instanceof EnderDragon dragon) {
            LivingEntity target = PossessionAim.findLiving(dragon, player,
                    VanillaInstinctsServerConfig.possessionRangedRange());
            if (target == null) return;
            dragon.getPhaseManager().setPhase(EnderDragonPhase.STRAFE_PLAYER);
            DragonStrafePlayerPhase phase = dragon.getPhaseManager()
                    .getPhase(EnderDragonPhase.STRAFE_PLAYER);
            phase.setTarget(target);
            session.secondaryReadyAt = gameTime + 120L;
            return;
        }

        if (mob instanceof Witch witch) {
            if (!player.getMainHandItem().isEmpty()) return;
            boolean thrown = withPlayerProxy(player, witch,
                    () -> PossessionWitchPotionController.throwRandomSplash(
                            player, witch));
            if (thrown) session.secondaryReadyAt = gameTime + 20L;
            return;
        }

        if (mob instanceof EnderMan enderman) {
            if (!player.getMainHandItem().isEmpty()) return;
            if (teleportEndermanToAim(enderman)) {
                session.secondaryReadyAt = gameTime
                        + ENDERMAN_TELEPORT_COOLDOWN_TICKS;
            }
            return;
        }

        // No generic AI pulse here: unsupported secondary abilities are a no-op
        // rather than allowing the mob to steal movement/navigation control.
    }

    private static boolean teleportEndermanToAim(EnderMan enderman) {
        HitResult aimed = enderman.pick(ENDERMAN_TELEPORT_RANGE, 1.0F, false);
        if (!(aimed instanceof BlockHitResult blockHit)
                || aimed.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        ServerLevel level = (ServerLevel) enderman.level();
        Vec3 origin = enderman.position();
        BlockPos preferred = blockHit.getBlockPos()
                .relative(blockHit.getDirection());
        AABB original = enderman.getBoundingBox();

        // Prefer the exact adjacent space selected by the crosshair, then look
        // a few blocks down for a safe floor. This makes top-face clicks land
        // on the block while side-face clicks can land beside walls/cliffs.
        for (int drop = 0; drop <= 6; drop++) {
            BlockPos feet = preferred.below(drop);
            if (!level.getBlockState(feet.below()).blocksMotion()) continue;
            Vec3 target = new Vec3(feet.getX() + 0.5D, feet.getY(),
                    feet.getZ() + 0.5D);
            if (target.distanceToSqr(origin)
                    > ENDERMAN_TELEPORT_RANGE * ENDERMAN_TELEPORT_RANGE) {
                continue;
            }
            Vec3 delta = target.subtract(origin);
            if (!level.noCollision(enderman, original.move(delta))) continue;
            return performManualEndermanTeleport(enderman, target);
        }
        return false;
    }

    private static boolean performManualEndermanTeleport(EnderMan enderman,
                                                           Vec3 target) {
        UUID previous = MANUAL_ENDERMAN_TELEPORT.get();
        MANUAL_ENDERMAN_TELEPORT.set(enderman.getUUID());
        try {
            return enderman.randomTeleport(target.x, target.y, target.z, true);
        } finally {
            if (previous == null) {
                MANUAL_ENDERMAN_TELEPORT.remove();
            } else {
                MANUAL_ENDERMAN_TELEPORT.set(previous);
            }
        }
    }

    private static EnderDragonPhase<?> currentDragonPhase(Mob mob) {
        if (!(mob instanceof EnderDragon dragon)) return null;
        DragonPhaseInstance current = dragon.getPhaseManager().getCurrentPhase();
        return current == null ? null : current.getPhase();
    }

    private static GoalSelector goalSelector(Mob mob) {
        return ((MobGoalSelectorAccessor) mob)
                .vanillaInstincts$getGoalSelector();
    }

    private static GoalSelector targetSelector(Mob mob) {
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

    private static void killController(ServerPlayer player) {
        if (player == null || !player.isAlive()) return;
        // genericKill is the same bypassing source used by /kill semantics, so a
        // possessed mob death also kills Creative controllers reliably.
        player.setInvulnerable(false);
        player.hurtServer(player.level(), player.damageSources().genericKill(), Float.MAX_VALUE);
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
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
        private Mob mob;
        private final boolean previousNoAi;
        private final boolean previousNoGravity;
        private final boolean previousMobInvulnerable;
        private final boolean previousAggressive;
        private final LivingEntity previousTarget;
        private final EnumSet<Goal.Flag> previousGoalDisabled;
        private final EnumSet<Goal.Flag> previousTargetDisabled;
        private final EnderDragonPhase<?> previousDragonPhase;
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
        private boolean bowDrawing;
        private InteractionHand bowHand;
        private long bowStartedAt;

        private Session(Mob mob, boolean previousNoAi,
                        boolean previousNoGravity,
                        boolean previousMobInvulnerable,
                        boolean previousAggressive, LivingEntity previousTarget,
                        float yaw, float pitch,
                        EnumSet<Goal.Flag> previousGoalDisabled,
                        EnumSet<Goal.Flag> previousTargetDisabled,
                        EnderDragonPhase<?> previousDragonPhase) {
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
            this.lastFoodPosition = mob.position();
        }
    }

    private record ConsumableProxySnapshot(
            float health, float absorption,
            Map<Holder<MobEffect>, MobEffectInstance> effects) {
        private static ConsumableProxySnapshot capture(ServerPlayer player) {
            Map<Holder<MobEffect>, MobEffectInstance> effects =
                    new LinkedHashMap<>();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                effects.put(effect.getEffect(), new MobEffectInstance(effect));
            }
            return new ConsumableProxySnapshot(player.getHealth(),
                    player.getAbsorptionAmount(), effects);
        }

        private void restore(ServerPlayer player) {
            player.removeAllEffects();
            for (MobEffectInstance effect : effects.values()) {
                player.addEffect(new MobEffectInstance(effect));
            }
            player.setHealth(Math.min(player.getMaxHealth(),
                    Math.max(0.1F, health)));
            player.setAbsorptionAmount(Math.max(0.0F, absorption));
        }
    }
}

package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.GolemRules;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import fr.vanillainstincts.compat.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import fr.vanillainstincts.compat.Vec3;
/**
 * Réparation visible et prioritaire des golems par un maçon ou un métier de
 * forge. Le golem blessé peut lui-même chercher un réparateur. Chaque lingot
 * est réellement lancé et peut être intercepté par un joueur. Dans ce cas, le
 * golem poursuit le voleur jusqu'à sa mort dans le jeu, puis retourne auprès
 * de l'artisan et reprend la réparation.
 */
public final class GolemRepairController {
    public static final String REPAIR_INGOT = "vanillainstincts_golem_repair_ingot";
    public static final String REPAIR_GOLEM = "vanillainstincts_golem_repair_golem";
    public static final String REPAIR_ARTISAN = "vanillainstincts_golem_repair_artisan";
    private static final String REPAIR_APPLY_AT =
            "vanillainstincts_golem_repair_apply_at";

    private static final String REPAIR_READY_AT = "vanillainstincts_repair_ready_at";
    private static final String REPAIR_SCAN_AT = "vanillainstincts_repair_scan_at";
    private static final String GOLEM_SCAN_AT = "vanillainstincts_golem_repair_scan_at";

    private static final Map<WorldServer, Map<UUID, Session>> SESSIONS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<WorldServer, List<VisualIngot>> VISUAL_INGOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private GolemRepairController() {
    }

    public static boolean contributeSmith(EntityVillager artisan,
                                          VillagerRuntimeState state,
                                          MobDecisionPlan plan,
                                          WorldServer level,
                                          long gameTime) {
        if (artisan == null || artisan.isChild()
                || !isRepairProfession(LegacyVillagerProfession.of(artisan))
                || gameTime < artisan.getEntityData()
                .getLong(REPAIR_READY_AT)) {
            return false;
        }

        Session session = sessionForArtisan(level, artisan.getUniqueID());
        if (artisan.isTrading() || state.danger(gameTime) != null) {
            if (session != null && !session.awaitingRetribution) {
                cancel(level, session);
            }
            return false;
        }
        if (session == null) {
            if (gameTime < artisan.getEntityData().getLong(REPAIR_SCAN_AT)) {
                return false;
            }
            artisan.getEntityData().setLong(REPAIR_SCAN_AT,
                    gameTime + ProfessionRules.VILLAGE_PROFESSION_SCAN_INTERVAL_TICKS);
            EntityIronGolem target = nearestRepairableGolem(artisan, level);
            if (target == null) return false;
            session = createSession(level, artisan, target, gameTime);
            if (session == null) return false;
        }

        EntityIronGolem golem = resolveGolem(level, session.golemId);
        if (golem == null) {
            cancel(level, session);
            return false;
        }
        if (session.awaitingRetribution) {
            Session held = session;
            plan.offerSpecial(VanillaInstinctsState.GOLEM_REPAIR,
                    ActionOwner.VILLAGER_PROFESSION,
                    VillageConstructionRules.PRIORITY_GOLEM_REPAIR,
                    VillageConstructionRules.STATE_HOLD_GOLEM_REPAIR_TICKS,
                    () -> {
                        artisan.getNavigator().clearPathEntity();
                        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(artisan, golem,
                                30.0F, 30.0F);
                        held.expiresAt = Math.max(held.expiresAt,
                                gameTime + 100L);
                    });
            return true;
        }
        if (golem.getAttackTarget() != null) {
            cancel(level, session);
            return false;
        }
        if (golem.getHealth() >= golem.getMaxHealth()) {
            complete(level, session, artisan, golem, gameTime);
            return false;
        }

        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(artisan, golem)
                > VillageConstructionRules.GOLEM_REPAIR_MEETING_DISTANCE_SQR) {
            plan.offerNavigation(VanillaInstinctsState.GOLEM_REPAIR,
                    ActionOwner.VILLAGER_PROFESSION,
                    VillageConstructionRules.PRIORITY_GOLEM_REPAIR,
                    midpoint(fr.vanillainstincts.compat.Minecraft17Compat.position(artisan), fr.vanillainstincts.compat.Minecraft17Compat.position(golem)),
                    VillageConstructionRules.GOLEM_REPAIR_SMITH_SPEED,
                    VillageConstructionRules.STATE_HOLD_GOLEM_REPAIR_TICKS, null);
            return true;
        }

        Session held = session;
        plan.offerSpecial(VanillaInstinctsState.GOLEM_REPAIR,
                ActionOwner.VILLAGER_PROFESSION,
                VillageConstructionRules.PRIORITY_GOLEM_REPAIR + 4,
                VillageConstructionRules.STATE_HOLD_GOLEM_REPAIR_TICKS,
                () -> {
                    artisan.getNavigator().clearPathEntity();
                    fr.vanillainstincts.compat.Minecraft112Compat.lookAt(artisan, golem, 30.0F, 30.0F);
                    if (gameTime >= held.nextDropAt
                            && held.ingotsRemaining > 0) {
                        dropOneIngot(level, held, artisan, golem, gameTime);
                    }
                });
        return true;
    }

    public static boolean contributeGolem(EntityIronGolem golem,
                                          MobDecisionPlan plan,
                                          WorldServer level,
                                          long gameTime) {
        Session session = levelSessions(level).get(golem.getUniqueID());
        if (session == null && golem.isEntityAlive()
                && golem.getHealth() < golem.getMaxHealth()
                && golem.getAttackTarget() == null
                && gameTime >= golem.getEntityData()
                .getLong(GOLEM_SCAN_AT)) {
            golem.getEntityData().setLong(GOLEM_SCAN_AT,
                    gameTime + VillageConstructionRules.GOLEM_REPAIR_GOLEM_SCAN_TICKS);
            EntityVillager artisan = nearestRepairerForGolem(golem, level);
            if (artisan != null) {
                session = createSession(level, artisan, golem, gameTime);
            }
        }
        if (session == null) return false;

        EntityVillager artisan = resolveVillager(level, session.artisanId);
        if (artisan == null || gameTime > session.expiresAt) {
            cancel(level, session);
            return false;
        }

        if (session.awaitingRetribution) {
            EntityPlayerMP thief = resolvePlayer(level, session.thiefId);
            if (thief == null || !thief.isEntityAlive()
                    || gameTime > session.retributionUntil) {
                endRetribution(session, golem, gameTime);
            } else {
                Session held = session;
                plan.offerSpecial(VanillaInstinctsState.GOLEM_PURSUIT,
                        ActionOwner.VILLAGE_DEFENSE,
                        GolemRules.PRIORITY_GOLEM_PURSUIT + 4,
                        VillageConstructionRules.STATE_HOLD_GOLEM_REPAIR_TICKS,
                        () -> {
                            golem.setAttackTarget(thief);
                            held.expiresAt = Math.max(held.expiresAt,
                                    gameTime + 100L);
                        });
                return true;
            }
        }

        if (golem.getAttackTarget() != null) return false;
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(golem, artisan)
                > VillageConstructionRules.GOLEM_REPAIR_MEETING_DISTANCE_SQR) {
            plan.offerNavigation(VanillaInstinctsState.GOLEM_REPAIR,
                    ActionOwner.GOLEM_PATROL,
                    VillageConstructionRules.PRIORITY_GOLEM_REPAIR - 2,
                    midpoint(fr.vanillainstincts.compat.Minecraft17Compat.position(golem), fr.vanillainstincts.compat.Minecraft17Compat.position(artisan)),
                    VillageConstructionRules.GOLEM_REPAIR_GOLEM_SPEED,
                    VillageConstructionRules.STATE_HOLD_GOLEM_REPAIR_TICKS, null);
        } else {
            plan.offerSpecial(VanillaInstinctsState.GOLEM_REPAIR,
                    ActionOwner.GOLEM_PATROL,
                    VillageConstructionRules.PRIORITY_GOLEM_REPAIR - 2,
                    VillageConstructionRules.STATE_HOLD_GOLEM_REPAIR_TICKS,
                    () -> {
                        golem.getNavigator().clearPathEntity();
                        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(golem, artisan,
                                30.0F, 30.0F);
                    });
        }
        return true;
    }

    public static void tickLevel(WorldServer level, long gameTime) {
        tickVisualIngots(level, gameTime);
        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, Session> entry : fr.vanillainstincts.compat.LegacyJava8.copyList(
                levelSessions(level).entrySet())) {
            Session session = entry.getValue();
            EntityVillager artisan = resolveVillager(level, session.artisanId);
            EntityIronGolem golem = resolveGolem(level, session.golemId);
            if (artisan == null || golem == null || gameTime > session.expiresAt) {
                discardVisuals(level, session.golemId);
                finished.add(entry.getKey());
                continue;
            }
            if (session.awaitingRetribution) {
                EntityPlayerMP thief = resolvePlayer(level, session.thiefId);
                if (thief == null || !thief.isEntityAlive()
                        || gameTime > session.retributionUntil) {
                    endRetribution(session, golem, gameTime);
                }
            }
            if (golem.getHealth() >= golem.getMaxHealth()
                    && !hasPendingVisual(level, session.golemId)) {
                complete(level, session, artisan, golem, gameTime);
                finished.add(entry.getKey());
            }
        }
        for (UUID id : finished) levelSessions(level).remove(id);
    }

    /** Rebuilds the visual-flight index after an item entity reload. */
    public static void tickLoadedRepairIngot(EntityItem item,
                                             WorldServer level,
                                             long gameTime) {
        if (item == null || !item.isEntityAlive()
                || !item.getEntityData().getBoolean(REPAIR_INGOT)
                || visualTracked(level, item.getUniqueID())) {
            return;
        }
        UUID golemId = fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(item.getEntityData(), REPAIR_GOLEM)
                ? fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(item.getEntityData(), REPAIR_GOLEM) : null;
        if (golemId == null) {
            unlockOrphanIngot(item);
            return;
        }
        long applyAt = item.getEntityData().getLong(REPAIR_APPLY_AT);
        visualIngots(level).add(new VisualIngot(item.getUniqueID(), golemId,
                Math.max(gameTime, applyAt)));
    }

    public static void onGolemKilledPlayer(EntityIronGolem golem,
                                           EntityPlayerMP player,
                                           long gameTime) {
        if (golem == null || player == null
                || !(golem.worldObj instanceof WorldServer)) return; WorldServer level = (WorldServer) (golem.worldObj);
        Session session = levelSessions(level).get(golem.getUniqueID());
        if (session == null || !session.awaitingRetribution
                || !player.getUniqueID().equals(session.thiefId)) {
            return;
        }
        session.retributionKills++;
        if (session.retributionKills >= 1) {
            endRetribution(session, golem, gameTime);
        }
    }

    public static int requiredIngotsForFullRepair(float health,
                                                   float maxHealth) {
        float missing = Math.max(0.0F, maxHealth - health);
        return (int) Math.ceil(missing / VillageConstructionRules.GOLEM_REPAIR_AMOUNT);
    }

    public static boolean canFullyRepair(float health, float maxHealth,
                                         int ironIngots) {
        int required = requiredIngotsForFullRepair(health, maxHealth);
        return required > 0 && ironIngots >= required;
    }

    public static float healthAfterIngots(float health, float maxHealth,
                                          int ironIngots) {
        return Math.min(maxHealth, Math.max(0.0F, health)
                + Math.max(0, ironIngots) * VillageConstructionRules.GOLEM_REPAIR_AMOUNT);
    }

    public static int ingotsNeededAfterTheft(int remaining) {
        return Math.max(0, remaining) + 1;
    }

    public static boolean retaliationSatisfied(int successfulKills) {
        return successfulKills >= 1;
    }

    public static boolean isRepairProfession(LegacyVillagerProfession profession) {
        return VillageConstructionCapability.isBuilderProfession(profession);
    }

    public static boolean usesArtificialServiceIron() {
        return true;
    }

    public static int countIronIngots(InventoryBasic inventory) {
        if (inventory == null) return 0;
        int total = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.getItem().equals(Items.iron_ingot)) total += stack.stackSize;
        }
        return total;
    }

    public static void clearLevel(WorldServer level) {
        SESSIONS.remove(level);
        VISUAL_INGOTS.remove(level);
    }

    private static Session createSession(WorldServer level, EntityVillager artisan,
                                         EntityIronGolem golem, long gameTime) {
        if (artisan == null || golem == null
                || levelSessions(level).containsKey(golem.getUniqueID())) {
            return null;
        }
        int required = requiredIngotsForFullRepair(golem.getHealth(),
                golem.getMaxHealth());
        // Le fer de réparation est un stock de service artificiel du métier :
        // la scène reste visible et volable, mais ne dépend plus de l'inventaire.
        Session session = new Session(artisan.getUniqueID(), golem.getUniqueID(),
                required, gameTime + VillageConstructionRules.GOLEM_REPAIR_SESSION_TICKS,
                gameTime);
        levelSessions(level).put(golem.getUniqueID(), session);
        return session;
    }

    private static EntityIronGolem nearestRepairableGolem(EntityVillager artisan,
                                                     WorldServer level) {
        return fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityIronGolem.class,
                        fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(artisan), 
                                VillageConstructionRules.GOLEM_REPAIR_RADIUS),
                        candidate -> candidate.isEntityAlive()
                                && candidate.getAttackTarget() == null
                                && candidate.getHealth()
                                < candidate.getMaxHealth()
                                && !levelSessions(level)
                                .containsKey(candidate.getUniqueID()))
                .stream()
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(artisan, value))))
                .orElse(null);
    }

    private static EntityVillager nearestRepairerForGolem(EntityIronGolem golem,
                                                     WorldServer level) {
        return fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityVillager.class,
                        fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(golem), 
                                VillageConstructionRules.GOLEM_REPAIR_RADIUS),
                        candidate -> candidate.isEntityAlive() && !candidate.isChild()
                                && !candidate.isTrading()
                                && isRepairProfession(LegacyVillagerProfession.of(candidate))
                                && sessionForArtisan(level,
                                candidate.getUniqueID()) == null)
                .stream()
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(golem, value))))
                .orElse(null);
    }

    private static void dropOneIngot(WorldServer level, Session session,
                                     EntityVillager artisan, EntityIronGolem golem,
                                     long gameTime) {
        // Lingot artificiel de service : il apparaît réellement dans le monde
        // et peut être volé, mais aucun stock d'inventaire n'est requis.
        Vec3 start =fr.vanillainstincts.compat.Minecraft112Compat.add(fr.vanillainstincts.compat.Minecraft17Compat.position(artisan), 0.0D, 1.15D, 0.0D);
        Vec3 destination =fr.vanillainstincts.compat.Minecraft112Compat.add(fr.vanillainstincts.compat.Minecraft17Compat.position(golem), 0.0D, 1.1D, 0.0D);
        Vec3 direction = destination.subtract(start);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(direction) > 1.0E-8D) direction = direction.normalize();

        EntityItem visual = new EntityItem(level, start.xCoord, start.yCoord, start.zCoord,
                new ItemStack(Items.iron_ingot));
        visual.setThrower(artisan.getUniqueID().toString());
        visual.getEntityData().setBoolean(
                VillagerFoodExchangeController.VILLAGE_ORIGIN, true);
        visual.getEntityData().setBoolean(REPAIR_INGOT, true);
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(visual.getEntityData(), REPAIR_GOLEM, golem.getUniqueID());
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(visual.getEntityData(), REPAIR_ARTISAN, artisan.getUniqueID());
        visual.getEntityData().setLong(REPAIR_APPLY_AT,
                gameTime
                        + VillageConstructionRules
                        .GOLEM_REPAIR_INGOT_TRAVEL_TICKS);
        fr.vanillainstincts.compat.Minecraft112Compat.setPickupDelay(visual, 32_767);
        fr.vanillainstincts.compat.Minecraft110Compat.setNoGravity(visual, true);
        fr.vanillainstincts.compat.Minecraft112Compat.setMotion(visual, fr.vanillainstincts.compat.Minecraft112Compat.scale(direction, 
                VillageConstructionRules.GOLEM_REPAIR_INGOT_VISUAL_SPEED));
        level.spawnEntityInWorld(visual);
        visualIngots(level).add(new VisualIngot(visual.getUniqueID(),
                golem.getUniqueID(), gameTime
                + VillageConstructionRules.GOLEM_REPAIR_INGOT_TRAVEL_TICKS));

        artisan.swingItem();
        // 1.12 villagers have no profession work-sound hook.
        session.ingotsRemaining--;
        session.nextDropAt = gameTime
                + VillageConstructionRules.GOLEM_REPAIR_DROP_INTERVAL_TICKS;
    }

    private static void tickVisualIngots(WorldServer level, long gameTime) {
        List<VisualIngot> visuals = visualIngots(level);
        List<VisualIngot> finished = new ArrayList<>();
        for (VisualIngot visual : fr.vanillainstincts.compat.LegacyJava8.copyList(visuals)) {
            Entity entity = fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, visual.entityId);
            if (!(entity instanceof EntityItem) || !((EntityItem) (entity)).isEntityAlive()) {
                finished.add(visual);
                continue;
            } EntityItem item = (EntityItem) (entity);
            EntityPlayerMP thief = nearestThief(level, item);
            if (thief != null) {
                stealIngot(level, visual, item, thief, gameTime);
                finished.add(visual);
                continue;
            }
            if (gameTime < visual.applyAt) continue;
            fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(item);
            EntityIronGolem golem = resolveGolem(level, visual.golemId);
            if (golem != null && golem.getHealth() < golem.getMaxHealth()) {
                golem.heal(VillageConstructionRules.GOLEM_REPAIR_AMOUNT);
                fr.vanillainstincts.compat.Minecraft17Compat.spawnParticle(level, EnumParticleTypes.VILLAGER_HAPPY,
                        golem.posX, golem.posY + 1.4D, golem.posZ,
                        8, 0.45D, 0.7D, 0.45D, 0.03D);
            }
            finished.add(visual);
        }
        visuals.removeAll(finished);
    }

    private static boolean visualTracked(WorldServer level, UUID itemId) {
        return visualIngots(level).stream()
                .anyMatch(visual -> visual.entityId.equals(itemId));
    }

    private static void unlockOrphanIngot(EntityItem item) {
        fr.vanillainstincts.compat.Minecraft110Compat.setNoGravity(item, false);
        fr.vanillainstincts.compat.Minecraft112Compat.setPickupDelay(item, 0);
        item.getEntityData().removeTag(REPAIR_INGOT);
        item.getEntityData().removeTag(REPAIR_GOLEM);
        item.getEntityData().removeTag(REPAIR_ARTISAN);
        item.getEntityData().removeTag(REPAIR_APPLY_AT);
    }

    private static EntityPlayerMP nearestThief(WorldServer level,
                                             EntityItem item) {
        return fr.vanillainstincts.compat.Minecraft112Compat.players(level).stream()
                .filter(player -> player.isEntityAlive() && !fr.vanillainstincts.compat.Minecraft17Compat.isSpectator(player)
                        && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(player, item)
                        <= VillageConstructionRules.GOLEM_REPAIR_THEFT_DISTANCE_SQR)
                .min(Comparator.comparingDouble(player ->
                        fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(player, item)))
                .orElse(null);
    }

    private static void stealIngot(WorldServer level, VisualIngot visual,
                                   EntityItem item, EntityPlayerMP thief,
                                   long gameTime) {
        ItemStack stolen = item.getEntityItem().copy();
        if (!fr.vanillainstincts.compat.Minecraft112Compat.give(thief, stolen) && !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stolen)) {
            fr.vanillainstincts.compat.Minecraft112Compat.drop(thief, stolen);
        }
        fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(item);

        Session session = levelSessions(level).get(visual.golemId);
        EntityIronGolem golem = resolveGolem(level, visual.golemId);
        if (session == null || golem == null) return;
        session.ingotsRemaining = ingotsNeededAfterTheft(
                session.ingotsRemaining);
        session.awaitingRetribution = true;
        session.thiefId = thief.getUniqueID();
        session.retributionKills = 0;
        session.retributionUntil = gameTime
                + VillageConstructionRules.GOLEM_REPAIR_RETRIBUTION_TICKS;
        session.expiresAt = Math.max(session.expiresAt,
                session.retributionUntil
                        + VillageConstructionRules.GOLEM_REPAIR_SESSION_TICKS);
        golem.setAttackTarget(thief);
    }

    private static void endRetribution(Session session, EntityIronGolem golem,
                                       long gameTime) {
        session.awaitingRetribution = false;
        session.thiefId = null;
        session.retributionKills = 0;
        session.retributionUntil = 0L;
        session.nextDropAt = gameTime
                + VillageConstructionRules.GOLEM_REPAIR_DROP_INTERVAL_TICKS;
        session.expiresAt = Math.max(session.expiresAt,
                gameTime + VillageConstructionRules.GOLEM_REPAIR_SESSION_TICKS);
        golem.setAttackTarget(null);
        golem.getNavigator().clearPathEntity();
    }

    private static void complete(WorldServer level, Session session,
                                 EntityVillager artisan, EntityIronGolem golem,
                                 long gameTime) {
        artisan.getEntityData().setLong(REPAIR_READY_AT,
                gameTime + VillageConstructionRules.GOLEM_REPAIR_COOLDOWN_TICKS);
        artisan.getNavigator().clearPathEntity();
        golem.getNavigator().clearPathEntity();
        fr.vanillainstincts.compat.Minecraft17Compat.spawnParticle(level, EnumParticleTypes.CRIT,
                golem.posX, golem.posY + 1.2D, golem.posZ,
                14, 0.6D, 0.8D, 0.6D, 0.04D);
        levelSessions(level).remove(session.golemId);
    }

    private static void cancel(WorldServer level, Session session) {
        if (session == null) return;
        discardVisuals(level, session.golemId);
        EntityIronGolem golem = resolveGolem(level, session.golemId);
        if (golem != null && session.awaitingRetribution) {
            golem.setAttackTarget(null);
        }
        levelSessions(level).remove(session.golemId);
    }

    private static void discardVisuals(WorldServer level, UUID golemId) {
        List<VisualIngot> visuals = visualIngots(level);
        for (VisualIngot visual : fr.vanillainstincts.compat.LegacyJava8.copyList(visuals)) {
            if (!visual.golemId.equals(golemId)) continue;
            Entity entity = fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, visual.entityId);
            if (entity != null) fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(entity);
            visuals.remove(visual);
        }
    }

    private static boolean hasPendingVisual(WorldServer level, UUID golemId) {
        return visualIngots(level).stream()
                .anyMatch(visual -> visual.golemId.equals(golemId));
    }

    private static Session sessionForArtisan(WorldServer level,
                                             UUID artisanId) {
        return levelSessions(level).values().stream()
                .filter(session -> session.artisanId.equals(artisanId))
                .findFirst().orElse(null);
    }

    private static EntityVillager resolveVillager(WorldServer level, UUID id) {
        Entity entity = id == null ? null : fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, id);
        return entity instanceof EntityVillager && ((EntityVillager) (entity)).isEntityAlive()
                ? ((EntityVillager) (entity)) : null;
    }

    private static EntityIronGolem resolveGolem(WorldServer level, UUID id) {
        Entity entity = id == null ? null : fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, id);
        return entity instanceof EntityIronGolem && ((EntityIronGolem) (entity)).isEntityAlive()
                ? ((EntityIronGolem) (entity)) : null;
    }

    private static EntityPlayerMP resolvePlayer(WorldServer level, UUID id) {
        EntityPlayer player = id == null ? null : fr.vanillainstincts.compat.Minecraft112Compat.player(level, id);
        return player instanceof EntityPlayerMP
                ? ((EntityPlayerMP) (player)) : null;
    }

    private static Vec3 midpoint(Vec3 first, Vec3 second) {
        return fr.vanillainstincts.compat.Minecraft112Compat.scale(first.add(second), 0.5D);
    }

    private static Map<UUID, Session> levelSessions(WorldServer level) {
        synchronized (SESSIONS) {
            return SESSIONS.computeIfAbsent(level, ignored -> new HashMap<>());
        }
    }

    private static List<VisualIngot> visualIngots(WorldServer level) {
        synchronized (VISUAL_INGOTS) {
            return VISUAL_INGOTS.computeIfAbsent(level,
                    ignored -> new ArrayList<>());
        }
    }

    private static final class Session {
        private final UUID artisanId;
        private final UUID golemId;
        private long expiresAt;
        private int ingotsRemaining;
        private long nextDropAt;
        private boolean awaitingRetribution;
        private UUID thiefId;
        private int retributionKills;
        private long retributionUntil;

        private Session(UUID artisanId, UUID golemId, int ingotsRemaining,
                        long expiresAt, long nextDropAt) {
            this.artisanId = artisanId;
            this.golemId = golemId;
            this.ingotsRemaining = ingotsRemaining;
            this.expiresAt = expiresAt;
            this.nextDropAt = nextDropAt;
        }
    }

    private static class VisualIngot {
        private final UUID entityId;
        private final UUID golemId;
        private final long applyAt;

        public VisualIngot(UUID entityId, UUID golemId, long applyAt) {
            this.entityId = entityId;
            this.golemId = golemId;
            this.applyAt = applyAt;
        }

        public UUID entityId() { return this.entityId; }

        public UUID golemId() { return this.golemId; }

        public long applyAt() { return this.applyAt; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VisualIngot)) return false;
            VisualIngot that = (VisualIngot) other;
            return java.util.Objects.equals(this.entityId, that.entityId) && java.util.Objects.equals(this.golemId, that.golemId) && this.applyAt == that.applyAt;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.entityId, this.golemId, this.applyAt); }

        @Override
        public String toString() {
            return "VisualIngot[" + "entityId=" + this.entityId + ", " + "golemId=" + this.golemId + ", " + "applyAt=" + this.applyAt + "]";
        }

    }
}

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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
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

    private static final Map<ServerLevel, Map<UUID, Session>> SESSIONS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerLevel, List<VisualIngot>> VISUAL_INGOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private GolemRepairController() {
    }

    public static boolean contributeSmith(Villager artisan,
                                          VillagerRuntimeState state,
                                          MobDecisionPlan plan,
                                          ServerLevel level,
                                          long gameTime) {
        if (artisan == null || artisan.isBaby()
                || !isRepairProfession(artisan.getVillagerData().profession().value())
                || gameTime < fr.vanillainstincts.persistence.NbtCompat.getLong(artisan.getPersistentData(), REPAIR_READY_AT)) {
            return false;
        }

        Session session = sessionForArtisan(level, artisan.getUUID());
        if (artisan.isTrading() || state.danger(gameTime) != null) {
            if (session != null && !session.awaitingRetribution) {
                cancel(level, session);
            }
            return false;
        }
        if (session == null) {
            if (gameTime < fr.vanillainstincts.persistence.NbtCompat.getLong(artisan.getPersistentData(), REPAIR_SCAN_AT)) {
                return false;
            }
            artisan.getPersistentData().putLong(REPAIR_SCAN_AT,
                    gameTime + ProfessionRules.VILLAGE_PROFESSION_SCAN_INTERVAL_TICKS);
            IronGolem target = nearestRepairableGolem(artisan, level);
            if (target == null) return false;
            session = createSession(level, artisan, target, gameTime);
            if (session == null) return false;
        }

        IronGolem golem = resolveGolem(level, session.golemId);
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
                        artisan.getNavigation().stop();
                        artisan.getLookControl().setLookAt(golem,
                                30.0F, 30.0F);
                        held.expiresAt = Math.max(held.expiresAt,
                                gameTime + 100L);
                    });
            return true;
        }
        if (golem.getTarget() != null) {
            cancel(level, session);
            return false;
        }
        if (golem.getHealth() >= golem.getMaxHealth()) {
            complete(level, session, artisan, golem, gameTime);
            return false;
        }

        if (artisan.distanceToSqr(golem)
                > VillageConstructionRules.GOLEM_REPAIR_MEETING_DISTANCE_SQR) {
            plan.offerNavigation(VanillaInstinctsState.GOLEM_REPAIR,
                    ActionOwner.VILLAGER_PROFESSION,
                    VillageConstructionRules.PRIORITY_GOLEM_REPAIR,
                    midpoint(artisan.position(), golem.position()),
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
                    artisan.getNavigation().stop();
                    artisan.getLookControl().setLookAt(golem, 30.0F, 30.0F);
                    if (gameTime >= held.nextDropAt
                            && held.ingotsRemaining > 0) {
                        dropOneIngot(level, held, artisan, golem, gameTime);
                    }
                });
        return true;
    }

    public static boolean contributeGolem(IronGolem golem,
                                          MobDecisionPlan plan,
                                          ServerLevel level,
                                          long gameTime) {
        Session session = levelSessions(level).get(golem.getUUID());
        if (session == null && golem.isAlive()
                && golem.getHealth() < golem.getMaxHealth()
                && golem.getTarget() == null
                && gameTime >= fr.vanillainstincts.persistence.NbtCompat.getLong(golem.getPersistentData(), GOLEM_SCAN_AT)) {
            golem.getPersistentData().putLong(GOLEM_SCAN_AT,
                    gameTime + VillageConstructionRules.GOLEM_REPAIR_GOLEM_SCAN_TICKS);
            Villager artisan = nearestRepairerForGolem(golem, level);
            if (artisan != null) {
                session = createSession(level, artisan, golem, gameTime);
            }
        }
        if (session == null) return false;

        Villager artisan = resolveVillager(level, session.artisanId);
        if (artisan == null || gameTime > session.expiresAt) {
            cancel(level, session);
            return false;
        }

        if (session.awaitingRetribution) {
            ServerPlayer thief = resolvePlayer(level, session.thiefId);
            if (thief == null || !thief.isAlive()
                    || gameTime > session.retributionUntil) {
                endRetribution(session, golem, gameTime);
            } else {
                Session held = session;
                plan.offerSpecial(VanillaInstinctsState.GOLEM_PURSUIT,
                        ActionOwner.VILLAGE_DEFENSE,
                        GolemRules.PRIORITY_GOLEM_PURSUIT + 4,
                        VillageConstructionRules.STATE_HOLD_GOLEM_REPAIR_TICKS,
                        () -> {
                            golem.setTarget(thief);
                            held.expiresAt = Math.max(held.expiresAt,
                                    gameTime + 100L);
                        });
                return true;
            }
        }

        if (golem.getTarget() != null) return false;
        if (golem.distanceToSqr(artisan)
                > VillageConstructionRules.GOLEM_REPAIR_MEETING_DISTANCE_SQR) {
            plan.offerNavigation(VanillaInstinctsState.GOLEM_REPAIR,
                    ActionOwner.GOLEM_PATROL,
                    VillageConstructionRules.PRIORITY_GOLEM_REPAIR - 2,
                    midpoint(golem.position(), artisan.position()),
                    VillageConstructionRules.GOLEM_REPAIR_GOLEM_SPEED,
                    VillageConstructionRules.STATE_HOLD_GOLEM_REPAIR_TICKS, null);
        } else {
            plan.offerSpecial(VanillaInstinctsState.GOLEM_REPAIR,
                    ActionOwner.GOLEM_PATROL,
                    VillageConstructionRules.PRIORITY_GOLEM_REPAIR - 2,
                    VillageConstructionRules.STATE_HOLD_GOLEM_REPAIR_TICKS,
                    () -> {
                        golem.getNavigation().stop();
                        golem.getLookControl().setLookAt(artisan,
                                30.0F, 30.0F);
                    });
        }
        return true;
    }

    public static void tickLevel(ServerLevel level, long gameTime) {
        tickVisualIngots(level, gameTime);
        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, Session> entry : List.copyOf(
                levelSessions(level).entrySet())) {
            Session session = entry.getValue();
            Villager artisan = resolveVillager(level, session.artisanId);
            IronGolem golem = resolveGolem(level, session.golemId);
            if (artisan == null || golem == null || gameTime > session.expiresAt) {
                discardVisuals(level, session.golemId);
                finished.add(entry.getKey());
                continue;
            }
            if (session.awaitingRetribution) {
                ServerPlayer thief = resolvePlayer(level, session.thiefId);
                if (thief == null || !thief.isAlive()
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
    public static void tickLoadedRepairIngot(ItemEntity item,
                                             ServerLevel level,
                                             long gameTime) {
        if (item == null || !item.isAlive()
                || !fr.vanillainstincts.persistence.NbtCompat.getBoolean(item.getPersistentData(), REPAIR_INGOT)
                || visualTracked(level, item.getUUID())) {
            return;
        }
        UUID golemId = fr.vanillainstincts.persistence.NbtCompat.hasUuid(item.getPersistentData(), REPAIR_GOLEM)
                ? fr.vanillainstincts.persistence.NbtCompat.getUuid(item.getPersistentData(), REPAIR_GOLEM) : null;
        if (golemId == null) {
            unlockOrphanIngot(item);
            return;
        }
        long applyAt = fr.vanillainstincts.persistence.NbtCompat.getLong(item.getPersistentData(), REPAIR_APPLY_AT);
        visualIngots(level).add(new VisualIngot(item.getUUID(), golemId,
                Math.max(gameTime, applyAt)));
    }

    public static void onGolemKilledPlayer(IronGolem golem,
                                           ServerPlayer player,
                                           long gameTime) {
        if (golem == null || player == null
                || !(golem.level() instanceof ServerLevel level)) return;
        Session session = levelSessions(level).get(golem.getUUID());
        if (session == null || !session.awaitingRetribution
                || !player.getUUID().equals(session.thiefId)) {
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

    public static boolean isRepairProfession(VillagerProfession profession) {
        return VillageConstructionCapability.isBuilderProfession(profession);
    }

    public static boolean usesArtificialServiceIron() {
        return true;
    }

    public static int countIronIngots(SimpleContainer inventory) {
        if (inventory == null) return 0;
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.IRON_INGOT)) total += stack.getCount();
        }
        return total;
    }

    public static void clearLevel(ServerLevel level) {
        SESSIONS.remove(level);
        VISUAL_INGOTS.remove(level);
    }

    private static Session createSession(ServerLevel level, Villager artisan,
                                         IronGolem golem, long gameTime) {
        if (artisan == null || golem == null
                || levelSessions(level).containsKey(golem.getUUID())) {
            return null;
        }
        int required = requiredIngotsForFullRepair(golem.getHealth(),
                golem.getMaxHealth());
        // Le fer de réparation est un stock de service artificiel du métier :
        // la scène reste visible et volable, mais ne dépend plus de l'inventaire.
        Session session = new Session(artisan.getUUID(), golem.getUUID(),
                required, gameTime + VillageConstructionRules.GOLEM_REPAIR_SESSION_TICKS,
                gameTime);
        levelSessions(level).put(golem.getUUID(), session);
        return session;
    }

    private static IronGolem nearestRepairableGolem(Villager artisan,
                                                     ServerLevel level) {
        return level.getEntitiesOfClass(IronGolem.class,
                        artisan.getBoundingBox().inflate(
                                VillageConstructionRules.GOLEM_REPAIR_RADIUS),
                        candidate -> candidate.isAlive()
                                && candidate.getTarget() == null
                                && candidate.getHealth()
                                < candidate.getMaxHealth()
                                && !levelSessions(level)
                                .containsKey(candidate.getUUID()))
                .stream()
                .min(Comparator.comparingDouble(artisan::distanceToSqr))
                .orElse(null);
    }

    private static Villager nearestRepairerForGolem(IronGolem golem,
                                                     ServerLevel level) {
        return level.getEntitiesOfClass(Villager.class,
                        golem.getBoundingBox().inflate(
                                VillageConstructionRules.GOLEM_REPAIR_RADIUS),
                        candidate -> candidate.isAlive() && !candidate.isBaby()
                                && !candidate.isTrading()
                                && isRepairProfession(candidate.getVillagerData().profession().value())
                                && sessionForArtisan(level,
                                candidate.getUUID()) == null)
                .stream()
                .min(Comparator.comparingDouble(golem::distanceToSqr))
                .orElse(null);
    }

    private static void dropOneIngot(ServerLevel level, Session session,
                                     Villager artisan, IronGolem golem,
                                     long gameTime) {
        // Lingot artificiel de service : il apparaît réellement dans le monde
        // et peut être volé, mais aucun stock d'inventaire n'est requis.
        Vec3 start = artisan.position().add(0.0D, 1.15D, 0.0D);
        Vec3 destination = golem.position().add(0.0D, 1.1D, 0.0D);
        Vec3 direction = destination.subtract(start);
        if (direction.lengthSqr() > 1.0E-8D) direction = direction.normalize();

        ItemEntity visual = new ItemEntity(level, start.x, start.y, start.z,
                new ItemStack(Items.IRON_INGOT));
        visual.setThrower(artisan);
        visual.getPersistentData().putBoolean(
                VillagerFoodExchangeController.VILLAGE_ORIGIN, true);
        visual.getPersistentData().putBoolean(REPAIR_INGOT, true);
        fr.vanillainstincts.persistence.NbtCompat.putUuid(visual.getPersistentData(), REPAIR_GOLEM, golem.getUUID());
        fr.vanillainstincts.persistence.NbtCompat.putUuid(visual.getPersistentData(), REPAIR_ARTISAN, artisan.getUUID());
        visual.getPersistentData().putLong(REPAIR_APPLY_AT,
                gameTime
                        + VillageConstructionRules
                        .GOLEM_REPAIR_INGOT_TRAVEL_TICKS);
        visual.setPickUpDelay(32_767);
        visual.setNoGravity(true);
        visual.setDeltaMovement(direction.scale(
                VillageConstructionRules.GOLEM_REPAIR_INGOT_VISUAL_SPEED));
        level.addFreshEntity(visual);
        visualIngots(level).add(new VisualIngot(visual.getUUID(),
                golem.getUUID(), gameTime
                + VillageConstructionRules.GOLEM_REPAIR_INGOT_TRAVEL_TICKS));

        artisan.swing(InteractionHand.MAIN_HAND);
        artisan.playWorkSound();
        session.ingotsRemaining--;
        session.nextDropAt = gameTime
                + VillageConstructionRules.GOLEM_REPAIR_DROP_INTERVAL_TICKS;
    }

    private static void tickVisualIngots(ServerLevel level, long gameTime) {
        List<VisualIngot> visuals = visualIngots(level);
        List<VisualIngot> finished = new ArrayList<>();
        for (VisualIngot visual : List.copyOf(visuals)) {
            Entity entity = level.getEntity(visual.entityId);
            if (!(entity instanceof ItemEntity item) || !item.isAlive()) {
                finished.add(visual);
                continue;
            }
            ServerPlayer thief = nearestThief(level, item);
            if (thief != null) {
                stealIngot(level, visual, item, thief, gameTime);
                finished.add(visual);
                continue;
            }
            if (gameTime < visual.applyAt) continue;
            item.discard();
            IronGolem golem = resolveGolem(level, visual.golemId);
            if (golem != null && golem.getHealth() < golem.getMaxHealth()) {
                golem.heal(VillageConstructionRules.GOLEM_REPAIR_AMOUNT);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        golem.getX(), golem.getY() + 1.4D, golem.getZ(),
                        8, 0.45D, 0.7D, 0.45D, 0.03D);
            }
            finished.add(visual);
        }
        visuals.removeAll(finished);
    }

    private static boolean visualTracked(ServerLevel level, UUID itemId) {
        return visualIngots(level).stream()
                .anyMatch(visual -> visual.entityId.equals(itemId));
    }

    private static void unlockOrphanIngot(ItemEntity item) {
        item.setNoGravity(false);
        item.setPickUpDelay(0);
        item.getPersistentData().remove(REPAIR_INGOT);
        item.getPersistentData().remove(REPAIR_GOLEM);
        item.getPersistentData().remove(REPAIR_ARTISAN);
        item.getPersistentData().remove(REPAIR_APPLY_AT);
    }

    private static ServerPlayer nearestThief(ServerLevel level,
                                             ItemEntity item) {
        return level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator()
                        && player.distanceToSqr(item)
                        <= VillageConstructionRules.GOLEM_REPAIR_THEFT_DISTANCE_SQR)
                .min(Comparator.comparingDouble(player ->
                        player.distanceToSqr(item)))
                .orElse(null);
    }

    private static void stealIngot(ServerLevel level, VisualIngot visual,
                                   ItemEntity item, ServerPlayer thief,
                                   long gameTime) {
        ItemStack stolen = item.getItem().copy();
        if (!thief.addItem(stolen) && !stolen.isEmpty()) {
            thief.drop(stolen, false);
        }
        item.discard();

        Session session = levelSessions(level).get(visual.golemId);
        IronGolem golem = resolveGolem(level, visual.golemId);
        if (session == null || golem == null) return;
        session.ingotsRemaining = ingotsNeededAfterTheft(
                session.ingotsRemaining);
        session.awaitingRetribution = true;
        session.thiefId = thief.getUUID();
        session.retributionKills = 0;
        session.retributionUntil = gameTime
                + VillageConstructionRules.GOLEM_REPAIR_RETRIBUTION_TICKS;
        session.expiresAt = Math.max(session.expiresAt,
                session.retributionUntil
                        + VillageConstructionRules.GOLEM_REPAIR_SESSION_TICKS);
        golem.setTarget(thief);
    }

    private static void endRetribution(Session session, IronGolem golem,
                                       long gameTime) {
        session.awaitingRetribution = false;
        session.thiefId = null;
        session.retributionKills = 0;
        session.retributionUntil = 0L;
        session.nextDropAt = gameTime
                + VillageConstructionRules.GOLEM_REPAIR_DROP_INTERVAL_TICKS;
        session.expiresAt = Math.max(session.expiresAt,
                gameTime + VillageConstructionRules.GOLEM_REPAIR_SESSION_TICKS);
        golem.setTarget(null);
        golem.getNavigation().stop();
    }

    private static void complete(ServerLevel level, Session session,
                                 Villager artisan, IronGolem golem,
                                 long gameTime) {
        artisan.getPersistentData().putLong(REPAIR_READY_AT,
                gameTime + VillageConstructionRules.GOLEM_REPAIR_COOLDOWN_TICKS);
        artisan.getNavigation().stop();
        golem.getNavigation().stop();
        level.sendParticles(ParticleTypes.CRIT,
                golem.getX(), golem.getY() + 1.2D, golem.getZ(),
                14, 0.6D, 0.8D, 0.6D, 0.04D);
        levelSessions(level).remove(session.golemId);
    }

    private static void cancel(ServerLevel level, Session session) {
        if (session == null) return;
        discardVisuals(level, session.golemId);
        IronGolem golem = resolveGolem(level, session.golemId);
        if (golem != null && session.awaitingRetribution) {
            golem.setTarget(null);
        }
        levelSessions(level).remove(session.golemId);
    }

    private static void discardVisuals(ServerLevel level, UUID golemId) {
        List<VisualIngot> visuals = visualIngots(level);
        for (VisualIngot visual : List.copyOf(visuals)) {
            if (!visual.golemId.equals(golemId)) continue;
            Entity entity = level.getEntity(visual.entityId);
            if (entity != null) entity.discard();
            visuals.remove(visual);
        }
    }

    private static boolean hasPendingVisual(ServerLevel level, UUID golemId) {
        return visualIngots(level).stream()
                .anyMatch(visual -> visual.golemId.equals(golemId));
    }

    private static Session sessionForArtisan(ServerLevel level,
                                             UUID artisanId) {
        return levelSessions(level).values().stream()
                .filter(session -> session.artisanId.equals(artisanId))
                .findFirst().orElse(null);
    }

    private static Villager resolveVillager(ServerLevel level, UUID id) {
        Entity entity = id == null ? null : level.getEntity(id);
        return entity instanceof Villager villager && villager.isAlive()
                ? villager : null;
    }

    private static IronGolem resolveGolem(ServerLevel level, UUID id) {
        Entity entity = id == null ? null : level.getEntity(id);
        return entity instanceof IronGolem golem && golem.isAlive()
                ? golem : null;
    }

    private static ServerPlayer resolvePlayer(ServerLevel level, UUID id) {
        Player player = id == null ? null : level.getPlayerByUUID(id);
        return player instanceof ServerPlayer serverPlayer
                ? serverPlayer : null;
    }

    private static Vec3 midpoint(Vec3 first, Vec3 second) {
        return first.add(second).scale(0.5D);
    }

    private static Map<UUID, Session> levelSessions(ServerLevel level) {
        synchronized (SESSIONS) {
            return SESSIONS.computeIfAbsent(level, ignored -> new HashMap<>());
        }
    }

    private static List<VisualIngot> visualIngots(ServerLevel level) {
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

    private record VisualIngot(UUID entityId, UUID golemId, long applyAt) {
    }
}

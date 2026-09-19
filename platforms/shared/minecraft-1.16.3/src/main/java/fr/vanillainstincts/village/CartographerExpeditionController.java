package fr.vanillainstincts.village;

import fr.vanillainstincts.compat.Minecraft119Compat;
import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.ai.VanillaInstinctsWorkLimiter;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.util.Hand;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.FilledMapItem;
import net.minecraft.world.storage.MapDecoration;
import net.minecraft.world.storage.MapData;
import net.minecraft.util.math.vector.Vector3d;
/** Cartographie. */
public final class CartographerExpeditionController {
    private static final String STAGE = "vanillainstincts_cartographer_stage";
    private static final String DESTINATION =
            "vanillainstincts_cartographer_destination";
    private static final String RETURN_POS = "vanillainstincts_cartographer_return";
    private static final String STAGE_AT = "vanillainstincts_cartographer_stage_at";
    private static final String READY_AT = "vanillainstincts_cartographer_ready_at";
    private static final String SEQUENCE = "vanillainstincts_cartographer_sequence";

    private static final int OUTWARD = 1;
    private static final int SURVEY = 2;
    private static final int RETURN = 3;

    private CartographerExpeditionController() {
    }

    public static void maintain(VillagerEntity villager, ServerWorld level,
                                long gameTime) {
        if (!isCartographer(villager)) return;
        int stage = stage(villager);
        if (stage == 0) return;

        if (stage == SURVEY) {
            maintainSurvey(villager, level, gameTime);
            return;
        }
        ItemStack visual = stage == OUTWARD
                ? new ItemStack(Items.COMPASS)
                : new ItemStack(Items.FILLED_MAP);
        villager.setItemInHand(Hand.MAIN_HAND, visual);
    }

    public static boolean contribute(VillagerEntity villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerWorld level,
                                     long gameTime) {
        if (!eligible(villager, state, level, gameTime)) return false;
        int stage = stage(villager);
        if (stage == OUTWARD) {
            return travel(villager, plan, level, gameTime, DESTINATION,
                    SURVEY);
        }
        if (stage == SURVEY) {
            plan.offerSpecial(VanillaInstinctsState.CARTOGRAPHER_EXPLORE,
                    ActionOwner.VILLAGER_PROFESSION,
                    VillageConstructionRules.PRIORITY_CARTOGRAPHER_EXPLORE,
                    ProfessionRules.STATE_HOLD_CARTOGRAPHER_SURVEY_TICKS,
                    () -> faceDestination(villager));
            return true;
        }
        if (stage == RETURN) {
            boolean active = travel(villager, plan, level, gameTime,
                    RETURN_POS, 0);
            if (!active) finish(villager, gameTime);
            return active;
        }
        return tryStart(villager, state, plan, level, gameTime);
    }

    public static boolean shouldFindTreasure(long sequence) {
        return Math.floorMod(sequence, ProfessionRules.CARTOGRAPHER_TREASURE_RATE)
                == 0L;
    }

    private static boolean shouldFindTreasureConfigured(long sequence) {
        return Math.floorMod(sequence,
                RuntimeConfig.snapshot().cartographerTreasureRate()) == 0L;
    }

    public static int expeditionRadius(long sequence) {
        int span = ProfessionRules.CARTOGRAPHER_EXPLORE_RADIUS_MAX
                - ProfessionRules.CARTOGRAPHER_EXPLORE_RADIUS_MIN + 1;
        return ProfessionRules.CARTOGRAPHER_EXPLORE_RADIUS_MIN
                + Math.floorMod(Long.hashCode(sequence * 31L), span);
    }

    private static boolean tryStart(VillagerEntity villager,
                                    VillagerRuntimeState state,
                                    MobDecisionPlan plan,
                                    ServerWorld level,
                                    long gameTime) {
        if (gameTime < villager.getPersistentData().getLong(READY_AT)
                || Math.floorMod(gameTime + villager.getId() * 19L,
                configuredTicks(ProfessionRules.CARTOGRAPHER_SCAN_TICKS)) != 0L) {
            return false;
        }
        VillagePoiScanner.refresh(villager, state, level, gameTime);
        long sequence = villager.getPersistentData().getLong(SEQUENCE) + 1L;
        Optional<Vector3d> destination = findDestination(villager, level,
                sequence);
        if (!destination.isPresent()) {
            setReady(villager, gameTime,
                    configuredTicks(ProfessionRules.CARTOGRAPHER_RETRY_TICKS));
            return false;
        }

        BlockPos job = state.jobSite(gameTime);
        BlockPos returnPos = job == null
                ? villager.blockPosition() : job.immutable();
        BlockPos target = new BlockPos(destination.get());
        Vector3d acceptedDestination = destination.get();
        plan.offerNavigation(VanillaInstinctsState.CARTOGRAPHER_EXPLORE,
                ActionOwner.VILLAGER_PROFESSION,
                VillageConstructionRules.PRIORITY_CARTOGRAPHER_EXPLORE,
                acceptedDestination, ProfessionRules.CARTOGRAPHER_SPEED,
                ProfessionRules.STATE_HOLD_CARTOGRAPHER_TRAVEL_TICKS, () -> {
                    villager.getPersistentData().putLong(SEQUENCE, sequence);
                    villager.getPersistentData().putLong(DESTINATION,
                            target.asLong());
                    villager.getPersistentData().putLong(RETURN_POS,
                            returnPos.asLong());
                    villager.getPersistentData().putInt(STAGE, OUTWARD);
                    villager.setItemInHand(Hand.MAIN_HAND,
                            new ItemStack(Items.COMPASS));
                });
        return true;
    }

    private static boolean travel(VillagerEntity villager, MobDecisionPlan plan,
                                  ServerWorld level, long gameTime,
                                  String key, int nextStage) {
        BlockPos target = readPos(villager, key);
        if (target == null || !level.hasChunkAt(target)) {
            finish(villager, gameTime);
            return false;
        }
        Optional<Vector3d> safe = SafePositionFinder.resolveGroundDestination(
                villager, Vector3d.atBottomCenterOf(target));
        if (!safe.isPresent()) {
            finish(villager, gameTime);
            return false;
        }
        if (villager.position().distanceToSqr(safe.get())
                <= ProfessionRules.CARTOGRAPHER_REACHED_DISTANCE_SQR) {
            if (nextStage == SURVEY) {
                villager.getPersistentData().putInt(STAGE, SURVEY);
                villager.getPersistentData().putLong(STAGE_AT,
                        gameTime + configuredTicks(ProfessionRules.CARTOGRAPHER_SURVEY_TICKS));
                villager.getNavigation().stop();
                faceDestination(villager);
                return true;
            }
            return false;
        }
        plan.offerNavigation(VanillaInstinctsState.CARTOGRAPHER_EXPLORE,
                ActionOwner.VILLAGER_PROFESSION,
                VillageConstructionRules.PRIORITY_CARTOGRAPHER_EXPLORE,
                safe.get(), ProfessionRules.CARTOGRAPHER_SPEED,
                ProfessionRules.STATE_HOLD_CARTOGRAPHER_TRAVEL_TICKS, null);
        return true;
    }

    private static void maintainSurvey(VillagerEntity villager, ServerWorld level,
                                       long gameTime) {
        villager.setItemInHand(Hand.MAIN_HAND,
                new ItemStack(Items.COMPASS));
        faceDestination(villager);
        if (VanillaInstinctsScheduler.isScheduled(villager,
                ProfessionRules.CARTOGRAPHER_SURVEY_ANIMATION_TICKS)) {
            villager.swing(Hand.MAIN_HAND);
            BlockPos pos = villager.blockPosition().above();
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, 2, 0.2D, 0.2D, 0.2D, 0.0D);
        }
        if (gameTime < villager.getPersistentData().getLong(STAGE_AT)) {
            return;
        }
        ItemStack map = createMap(villager, level, gameTime);
        VillageMarketController.recordProduction(level,
                villager.blockPosition(), map);
        long sequence = villager.getPersistentData().getLong(SEQUENCE);
        boolean socialRoute = Math.floorMod(sequence, 2L) == 0L;
        boolean represented = !socialRoute
                && RecoveredTradeController.addProducedOffer(villager, map, 1);
        if (!represented) {
            ItemStack social = Minecraft119Compat.copyWithCount(map, 1);
            ProfessionStockController.insert(villager.getInventory(), social);
            if (!social.isEmpty()) villager.spawnAtLocation(social);
            villager.getInventory().setChanged();
        }
        villager.setItemInHand(Hand.MAIN_HAND, map.copy());
        villager.getPersistentData().putInt(STAGE, RETURN);
    }

    private static ItemStack createMap(VillagerEntity villager, ServerWorld level,
                                       long gameTime) {
        BlockPos survey = readPos(villager, DESTINATION);
        if (survey == null) survey = villager.blockPosition();
        long sequence = villager.getPersistentData().getLong(SEQUENCE);
        if (shouldFindTreasureConfigured(sequence)
                && VanillaInstinctsWorkLimiter.allow(level,
                VanillaInstinctsWorkLimiter.Task.CARTOGRAPHER_TREASURE,
                gameTime, configuredTicks(ProfessionRules.CARTOGRAPHER_TREASURE_MIN_TICKS),
                ProfessionRules.CARTOGRAPHER_TREASURE_MIN_MILLIS)) {
            BlockPos treasure = level.findNearestMapFeature(
                    Structure.BURIED_TREASURE, survey,
                    ProfessionRules.CARTOGRAPHER_TREASURE_SEARCH_RADIUS, true);
            if (treasure != null) {
                ItemStack map = FilledMapItem.create(level, treasure.getX(),
                        treasure.getZ(), (byte) 1, true, true);
                FilledMapItem.renderBiomePreviewMap(level, map);
                MapData.addTargetDecoration(map, treasure,
                        "vanillainstincts_treasure", MapDecoration.Type.RED_X);
                return map;
            }
        }
        return FilledMapItem.create(level, survey.getX(), survey.getZ(),
                (byte) 1, true, true);
    }

    private static Optional<Vector3d> findDestination(VillagerEntity villager,
                                                  ServerWorld level,
                                                  long sequence) {
        int radius = expeditionRadius(sequence);
        int start = Math.floorMod(villager.getUUID().hashCode()
                + Long.hashCode(sequence), 16);
        int attempts = VanillaInstinctsScheduler.precisionLimit(level,
                villager, 16, PerformanceRules.MINIMUM_CANDIDATE_LIMIT);
        for (int offset = 0; offset < attempts; offset++) {
            double angle = (start + offset) * Math.PI * 2.0D / 16.0D;
            Vector3d requested = villager.position().add(
                    Math.cos(angle) * radius, 0.0D,
                    Math.sin(angle) * radius);
            BlockPos pos = new BlockPos(requested);
            if (!level.hasChunkAt(pos)) continue;
            Optional<Vector3d> safe = SafePositionFinder.resolveGroundDestination(
                    villager, requested);
            if (safe.isPresent()) return safe;
        }
        return Optional.empty();
    }

    private static void faceDestination(VillagerEntity villager) {
        BlockPos target = readPos(villager, DESTINATION);
        if (target != null) {
            villager.getLookControl().setLookAt(target.getX() + 0.5D,
                    target.getY() + 1.0D, target.getZ() + 0.5D,
                    30.0F, 30.0F);
        }
    }

    private static void finish(VillagerEntity villager, long gameTime) {
        villager.getPersistentData().remove(STAGE);
        villager.getPersistentData().remove(DESTINATION);
        villager.getPersistentData().remove(RETURN_POS);
        villager.getPersistentData().remove(STAGE_AT);
        villager.setItemInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        setReady(villager, gameTime,
                configuredTicks(ProfessionRules.CARTOGRAPHER_COOLDOWN_TICKS));
    }

    private static void setReady(VillagerEntity villager, long gameTime,
                                 int delay) {
        villager.getPersistentData().putLong(READY_AT,
                gameTime + Math.max(1, delay));
    }

    private static boolean eligible(VillagerEntity villager,
                                    VillagerRuntimeState state,
                                    ServerWorld level,
                                    long gameTime) {
        return isCartographer(villager) && !villager.isBaby()
                && !villager.isTrading() && state.danger(gameTime) == null
                && VillagerRoutineController.phaseFor(level.getDayTime())
                == VillagerSchedulePhase.WORK;
    }

    private static boolean isCartographer(VillagerEntity villager) {
        return villager != null && villager.getVillagerData().getProfession()
                == VillagerProfession.CARTOGRAPHER;
    }

    private static int stage(VillagerEntity villager) {
        return villager.getPersistentData().getInt(STAGE);
    }

    private static BlockPos readPos(VillagerEntity villager, String key) {
        return villager.getPersistentData().contains(key)
                ? BlockPos.of(villager.getPersistentData().getLong(key))
                : null;
    }

    private static int configuredTicks(int baseTicks) {
        return RuntimeConfig.interval(FeatureFlag.CARTOGRAPHER_EXPEDITIONS, baseTicks);
    }

}

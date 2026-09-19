package fr.vanillainstincts.village;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
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

    public static void maintain(Villager villager, ServerLevel level,
                                long gameTime) {
        if (!isCartographer(villager)) return;
        int stage = stage(villager);
        if (stage == 0) return;

        if (stage == SURVEY) {
            maintainSurvey(villager, level, gameTime);
            return;
        }
        ItemStack visual = stage == OUTWARD
                ? new ItemStack(Items.SPYGLASS)
                : new ItemStack(Items.FILLED_MAP);
        villager.setItemInHand(InteractionHand.MAIN_HAND, visual);
    }

    public static boolean contribute(Villager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerLevel level,
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

    private static boolean tryStart(Villager villager,
                                    VillagerRuntimeState state,
                                    MobDecisionPlan plan,
                                    ServerLevel level,
                                    long gameTime) {
        if (gameTime < villager.getPersistentData().getLong(READY_AT)
                || Math.floorMod(gameTime + villager.getId() * 19L,
                configuredTicks(ProfessionRules.CARTOGRAPHER_SCAN_TICKS)) != 0L) {
            return false;
        }
        VillagePoiScanner.refresh(villager, state, level, gameTime);
        long sequence = villager.getPersistentData().getLong(SEQUENCE) + 1L;
        Optional<Vec3> destination = findDestination(villager, level,
                sequence);
        if (destination.isEmpty()) {
            setReady(villager, gameTime,
                    configuredTicks(ProfessionRules.CARTOGRAPHER_RETRY_TICKS));
            return false;
        }

        BlockPos job = state.jobSite(gameTime);
        BlockPos returnPos = job == null
                ? villager.blockPosition() : job.immutable();
        BlockPos target = BlockPos.containing(destination.get());
        Vec3 acceptedDestination = destination.get();
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
                    villager.setItemInHand(InteractionHand.MAIN_HAND,
                            new ItemStack(Items.SPYGLASS));
                });
        return true;
    }

    private static boolean travel(Villager villager, MobDecisionPlan plan,
                                  ServerLevel level, long gameTime,
                                  String key, int nextStage) {
        BlockPos target = readPos(villager, key);
        if (target == null || !level.hasChunkAt(target)) {
            finish(villager, gameTime);
            return false;
        }
        Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                villager, Vec3.atBottomCenterOf(target));
        if (safe.isEmpty()) {
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

    private static void maintainSurvey(Villager villager, ServerLevel level,
                                       long gameTime) {
        villager.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(Items.SPYGLASS));
        faceDestination(villager);
        if (VanillaInstinctsScheduler.isScheduled(villager,
                ProfessionRules.CARTOGRAPHER_SURVEY_ANIMATION_TICKS)) {
            villager.swing(InteractionHand.MAIN_HAND);
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
            ItemStack social = map.copyWithCount(1);
            ProfessionStockController.insert(villager.getInventory(), social);
            if (!social.isEmpty()) villager.spawnAtLocation(level, social);
            villager.getInventory().setChanged();
        }
        villager.setItemInHand(InteractionHand.MAIN_HAND, map.copy());
        villager.getPersistentData().putInt(STAGE, RETURN);
    }

    private static ItemStack createMap(Villager villager, ServerLevel level,
                                       long gameTime) {
        BlockPos survey = readPos(villager, DESTINATION);
        if (survey == null) survey = villager.blockPosition();
        long sequence = villager.getPersistentData().getLong(SEQUENCE);
        if (shouldFindTreasureConfigured(sequence)
                && VanillaInstinctsWorkLimiter.allow(level,
                VanillaInstinctsWorkLimiter.Task.CARTOGRAPHER_TREASURE,
                gameTime, configuredTicks(ProfessionRules.CARTOGRAPHER_TREASURE_MIN_TICKS),
                ProfessionRules.CARTOGRAPHER_TREASURE_MIN_MILLIS)) {
            BlockPos treasure = level.findNearestMapStructure(
                    StructureTags.ON_TREASURE_MAPS, survey,
                    ProfessionRules.CARTOGRAPHER_TREASURE_SEARCH_RADIUS, true);
            if (treasure != null) {
                ItemStack map = MapItem.create(level, treasure.getX(),
                        treasure.getZ(), (byte) 1, true, true);
                MapItem.renderBiomePreviewMap(level, map);
                MapItemSavedData.addTargetDecoration(map, treasure,
                        "vanillainstincts_treasure", MapDecorationTypes.RED_X);
                return map;
            }
        }
        return MapItem.create(level, survey.getX(), survey.getZ(),
                (byte) 1, true, true);
    }

    private static Optional<Vec3> findDestination(Villager villager,
                                                  ServerLevel level,
                                                  long sequence) {
        int radius = expeditionRadius(sequence);
        int start = Math.floorMod(villager.getUUID().hashCode()
                + Long.hashCode(sequence), 16);
        int attempts = VanillaInstinctsScheduler.precisionLimit(level,
                villager, 16, PerformanceRules.MINIMUM_CANDIDATE_LIMIT);
        for (int offset = 0; offset < attempts; offset++) {
            double angle = (start + offset) * Math.PI * 2.0D / 16.0D;
            Vec3 requested = villager.position().add(
                    Math.cos(angle) * radius, 0.0D,
                    Math.sin(angle) * radius);
            BlockPos pos = BlockPos.containing(requested);
            if (!level.hasChunkAt(pos)) continue;
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    villager, requested);
            if (safe.isPresent()) return safe;
        }
        return Optional.empty();
    }

    private static void faceDestination(Villager villager) {
        BlockPos target = readPos(villager, DESTINATION);
        if (target != null) {
            villager.getLookControl().setLookAt(target.getX() + 0.5D,
                    target.getY() + 1.0D, target.getZ() + 0.5D,
                    30.0F, 30.0F);
        }
    }

    private static void finish(Villager villager, long gameTime) {
        villager.getPersistentData().remove(STAGE);
        villager.getPersistentData().remove(DESTINATION);
        villager.getPersistentData().remove(RETURN_POS);
        villager.getPersistentData().remove(STAGE_AT);
        villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        setReady(villager, gameTime,
                configuredTicks(ProfessionRules.CARTOGRAPHER_COOLDOWN_TICKS));
    }

    private static void setReady(Villager villager, long gameTime,
                                 int delay) {
        villager.getPersistentData().putLong(READY_AT,
                gameTime + Math.max(1, delay));
    }

    private static boolean eligible(Villager villager,
                                    VillagerRuntimeState state,
                                    ServerLevel level,
                                    long gameTime) {
        return isCartographer(villager) && !villager.isBaby()
                && !villager.isTrading() && state.danger(gameTime) == null
                && VillagerRoutineController.phaseFor(level.getDayTime())
                == VillagerSchedulePhase.WORK;
    }

    private static boolean isCartographer(Villager villager) {
        return villager != null && villager.getVillagerData().getProfession()
                == VillagerProfession.CARTOGRAPHER;
    }

    private static int stage(Villager villager) {
        return villager.getPersistentData().getInt(STAGE);
    }

    private static BlockPos readPos(Villager villager, String key) {
        return villager.getPersistentData().contains(key)
                ? BlockPos.of(villager.getPersistentData().getLong(key))
                : null;
    }

    private static int configuredTicks(int baseTicks) {
        return RuntimeConfig.interval(FeatureFlag.CARTOGRAPHER_EXPEDITIONS, baseTicks);
    }

}

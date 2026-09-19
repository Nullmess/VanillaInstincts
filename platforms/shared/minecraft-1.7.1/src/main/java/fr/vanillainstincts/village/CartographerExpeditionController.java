package fr.vanillainstincts.village;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

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
import fr.vanillainstincts.compat.BlockPos;
import fr.vanillainstincts.compat.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMap;
import fr.vanillainstincts.compat.Vec3;
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

    public static void maintain(EntityVillager villager, WorldServer level,
                                long gameTime) {
        if (!isCartographer(villager)) return;
        int stage = stage(villager);
        if (stage == 0) return;

        if (stage == SURVEY) {
            maintainSurvey(villager, level, gameTime);
            return;
        }
        ItemStack visual = stage == OUTWARD
                ? new ItemStack(Items.compass)
                : new ItemStack(Items.filled_map);
        villager.setCurrentItemOrArmor(0, visual);
    }

    public static boolean contribute(EntityVillager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     WorldServer level,
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

    private static boolean tryStart(EntityVillager villager,
                                    VillagerRuntimeState state,
                                    MobDecisionPlan plan,
                                    WorldServer level,
                                    long gameTime) {
        if (gameTime < villager.getEntityData().getLong(READY_AT)
                || Math.floorMod(gameTime + villager.getEntityId() * 19L,
                configuredTicks(ProfessionRules.CARTOGRAPHER_SCAN_TICKS)) != 0L) {
            return false;
        }
        VillagePoiScanner.refresh(villager, state, level, gameTime);
        long sequence = villager.getEntityData().getLong(SEQUENCE) + 1L;
        Optional<Vec3> destination = findDestination(villager, level,
                sequence);
        if (!destination.isPresent()) {
            setReady(villager, gameTime,
                    configuredTicks(ProfessionRules.CARTOGRAPHER_RETRY_TICKS));
            return false;
        }

        BlockPos job = state.jobSite(gameTime);
        BlockPos returnPos = job == null
                ? entityBlockPos(villager) : immutableBlockPos(job);
        BlockPos target = new BlockPos(destination.get());
        Vec3 acceptedDestination = destination.get();
        plan.offerNavigation(VanillaInstinctsState.CARTOGRAPHER_EXPLORE,
                ActionOwner.VILLAGER_PROFESSION,
                VillageConstructionRules.PRIORITY_CARTOGRAPHER_EXPLORE,
                acceptedDestination, ProfessionRules.CARTOGRAPHER_SPEED,
                ProfessionRules.STATE_HOLD_CARTOGRAPHER_TRAVEL_TICKS, () -> {
                    villager.getEntityData().setLong(SEQUENCE, sequence);
                    villager.getEntityData().setLong(DESTINATION,
                            target.toLong());
                    villager.getEntityData().setLong(RETURN_POS,
                            returnPos.toLong());
                    villager.getEntityData().setInteger(STAGE, OUTWARD);
                    villager.setCurrentItemOrArmor(0,
                            new ItemStack(Items.compass));
                });
        return true;
    }

    private static boolean travel(EntityVillager villager, MobDecisionPlan plan,
                                  WorldServer level, long gameTime,
                                  String key, int nextStage) {
        BlockPos target = readPos(villager, key);
        if (target == null || !fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, target)) {
            finish(villager, gameTime);
            return false;
        }
        Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                villager, Minecraft115VectorCompat.atBottomCenterOf(target));
        if (!safe.isPresent()) {
            finish(villager, gameTime);
            return false;
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(fr.vanillainstincts.compat.Minecraft17Compat.position(villager), safe.get())
                <= ProfessionRules.CARTOGRAPHER_REACHED_DISTANCE_SQR) {
            if (nextStage == SURVEY) {
                villager.getEntityData().setInteger(STAGE, SURVEY);
                villager.getEntityData().setLong(STAGE_AT,
                        gameTime + configuredTicks(ProfessionRules.CARTOGRAPHER_SURVEY_TICKS));
                villager.getNavigator().clearPathEntity();
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

    private static void maintainSurvey(EntityVillager villager, WorldServer level,
                                       long gameTime) {
        villager.setCurrentItemOrArmor(0,
                new ItemStack(Items.compass));
        faceDestination(villager);
        if (VanillaInstinctsScheduler.isScheduled(villager,
                ProfessionRules.CARTOGRAPHER_SURVEY_ANIMATION_TICKS)) {
            villager.swingItem();
            BlockPos pos = entityBlockPos(villager).up();
            fr.vanillainstincts.compat.Minecraft17Compat.spawnParticle(level, EnumParticleTypes.VILLAGER_HAPPY,
                    pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, 2, 0.2D, 0.2D, 0.2D, 0.0D);
        }
        if (gameTime < villager.getEntityData().getLong(STAGE_AT)) {
            return;
        }
        ItemStack map = createMap(villager, level, gameTime);
        VillageMarketController.recordProduction(level,
                entityBlockPos(villager), map);
        long sequence = villager.getEntityData().getLong(SEQUENCE);
        boolean socialRoute = Math.floorMod(sequence, 2L) == 0L;
        boolean represented = !socialRoute
                && RecoveredTradeController.addProducedOffer(villager, map, 1);
        if (!represented) {
            ItemStack social = Minecraft119Compat.copyWithCount(map, 1);
            ProfessionStockController.insert(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(villager), social);
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(social)) fr.vanillainstincts.compat.Minecraft112Compat.spawnItem(villager, social);
            fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(villager).markDirty();
        }
        villager.setCurrentItemOrArmor(0, map.copy());
        villager.getEntityData().setInteger(STAGE, RETURN);
    }

    private static ItemStack createMap(EntityVillager villager, WorldServer level,
                                       long gameTime) {
        BlockPos survey = readPos(villager, DESTINATION);
        if (survey == null) survey = entityBlockPos(villager);
        long sequence = villager.getEntityData().getLong(SEQUENCE);
        // Buried Treasure and RED_X map decorations were introduced after 1.12.
        // Keep the expedition useful by returning a normal survey map on this legacy target.
        return new ItemStack(Items.map);
    }

    private static Optional<Vec3> findDestination(EntityVillager villager,
                                                  WorldServer level,
                                                  long sequence) {
        int radius = expeditionRadius(sequence);
        int start = Math.floorMod(villager.getUniqueID().hashCode()
                + Long.hashCode(sequence), 16);
        int attempts = VanillaInstinctsScheduler.precisionLimit(level,
                villager, 16, PerformanceRules.MINIMUM_CANDIDATE_LIMIT);
        for (int offset = 0; offset < attempts; offset++) {
            double angle = (start + offset) * Math.PI * 2.0D / 16.0D;
            Vec3 requested =fr.vanillainstincts.compat.Minecraft112Compat.add(fr.vanillainstincts.compat.Minecraft17Compat.position(villager), 
                    Math.cos(angle) * radius, 0.0D,
                    Math.sin(angle) * radius);
            BlockPos pos = new BlockPos(requested);
            if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos)) continue;
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    villager, requested);
            if (safe.isPresent()) return safe;
        }
        return Optional.empty();
    }

    private static void faceDestination(EntityVillager villager) {
        BlockPos target = readPos(villager, DESTINATION);
        if (target != null) {
            fr.vanillainstincts.compat.Minecraft112Compat.lookAt(villager,
                    target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D,
                    30.0F, 30.0F);
        }
    }

    private static void finish(EntityVillager villager, long gameTime) {
        villager.getEntityData().removeTag(STAGE);
        villager.getEntityData().removeTag(DESTINATION);
        villager.getEntityData().removeTag(RETURN_POS);
        villager.getEntityData().removeTag(STAGE_AT);
        villager.setCurrentItemOrArmor(0, null);
        setReady(villager, gameTime,
                configuredTicks(ProfessionRules.CARTOGRAPHER_COOLDOWN_TICKS));
    }

    private static void setReady(EntityVillager villager, long gameTime,
                                 int delay) {
        villager.getEntityData().setLong(READY_AT,
                gameTime + Math.max(1, delay));
    }

    private static boolean eligible(EntityVillager villager,
                                    VillagerRuntimeState state,
                                    WorldServer level,
                                    long gameTime) {
        return isCartographer(villager) && !villager.isChild()
                && !villager.isTrading() && state.danger(gameTime) == null
                && VillagerRoutineController.phaseFor(level.getWorldTime())
                == VillagerSchedulePhase.WORK;
    }

    private static boolean isCartographer(EntityVillager villager) {
        return villager != null && LegacyVillagerProfession.of(villager)
                == LegacyVillagerProfession.CARTOGRAPHER;
    }

    private static int stage(EntityVillager villager) {
        return villager.getEntityData().getInteger(STAGE);
    }

    private static BlockPos readPos(EntityVillager villager, String key) {
        return villager.getEntityData().hasKey(key)
                ? BlockPos.fromLong(villager.getEntityData().getLong(key))
                : null;
    }

    private static int configuredTicks(int baseTicks) {
        return RuntimeConfig.interval(FeatureFlag.CARTOGRAPHER_EXPEDITIONS, baseTicks);
    }

}

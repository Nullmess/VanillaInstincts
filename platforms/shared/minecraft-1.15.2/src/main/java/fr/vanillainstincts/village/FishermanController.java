package fr.vanillainstincts.village;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.core.rules.ProfessionRules;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.Vec3d;
/** Pêche. */
public final class FishermanController {
    private static final String WATER_TARGET = "vanillainstincts_fisher_water_target";
    private static final String HOOK_ID = "vanillainstincts_fisher_hook_id";
    private static final String CATCH_ID = "vanillainstincts_fisher_catch_id";
    private static final String CAST_END_AT = "vanillainstincts_fisher_cast_end_at";
    private static final String BITE_AT = "vanillainstincts_fisher_bite_at";
    private static final String REEL_AT = "vanillainstincts_fisher_reel_at";
    private static final String COLLECT_AT = "vanillainstincts_fisher_collect_at";
    private static final String READY_AT = "vanillainstincts_fisher_ready_at";
    private static final String SEQUENCE = "vanillainstincts_fisher_sequence";
    private static final String FISHER_VISUAL = "vanillainstincts_fisher_visual";
    private static final String SCAN_CURSOR = "vanillainstincts_fisher_scan_cursor";

    private static final List<Item> CATCHES = fr.vanillainstincts.compat.LegacyJava8.listOf(
            Items.COD, Items.SALMON, Items.COD, Items.SALMON,
            Items.TROPICAL_FISH, Items.PUFFERFISH, Items.BOWL,
            Items.LEATHER, Items.STRING, Items.NAUTILUS_SHELL,
            Items.NAME_TAG, Items.SADDLE);
    private static final int[] WATER_COLUMNS = waterColumns();

    private FishermanController() {
    }

    public static boolean contribute(VillagerEntity fisher,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerWorld level,
                                     long gameTime) {
        if (fisher == null || fisher.isBaby() || fisher.isTrading()
                || state.danger(gameTime) != null
                || fisher.getVillagerData().getProfession()
                != VillagerProfession.FISHERMAN
                || VillagerRoutineController.phaseFor(level.getDayTime())
                != VillagerSchedulePhase.WORK) {
            return false;
        }
        if (hasUuid(fisher, HOOK_ID) || hasUuid(fisher, CATCH_ID)) return true;
        if (gameTime < fisher.getPersistentData().getLong(READY_AT)) return false;

        BlockPos water = readPos(fisher, WATER_TARGET);
        if (water == null || !isFishable(level, water)) {
            if (Math.floorMod(gameTime + fisher.getId(),
                    configuredTicks(ProfessionRules.FISHER_SCAN_INTERVAL_TICKS)) != 0L) {
                return false;
            }
            water = findWater(level, fisher);
            if (water == null) {
                fisher.getPersistentData().putLong(READY_AT,
                        gameTime + configuredTicks(ProfessionRules.FISHER_SCAN_INTERVAL_TICKS));
                return false;
            }
            fisher.getPersistentData().putLong(WATER_TARGET, water.asLong());
        }

        Vec3d bank = bankPosition(level, water, fisher);
        if (bank == null) return false;
        if (fisher.position().distanceToSqr(bank)
                > ProfessionRules.FISHER_WATER_REACH_SQR) {
            plan.offerNavigation(VanillaInstinctsState.FISHER_WORK,
                    ActionOwner.VILLAGER_PROFESSION,
                    ProfessionRules.PRIORITY_FISHER_WORK,
                    bank, ProfessionRules.FISHER_SPEED,
                    ProfessionRules.STATE_HOLD_FISHER_WORK_TICKS, null);
            return true;
        }

        BlockPos accepted = immutableBlockPos(water);
        plan.offerSpecial(VanillaInstinctsState.FISHER_WORK,
                ActionOwner.VILLAGER_PROFESSION,
                ProfessionRules.PRIORITY_FISHER_WORK + 2,
                ProfessionRules.STATE_HOLD_FISHER_WORK_TICKS,
                () -> cast(fisher, level, accepted, gameTime));
        return true;
    }

    public static void maintain(VillagerEntity fisher, ServerWorld level,
                                long gameTime) {
        if (fisher == null || fisher.getVillagerData().getProfession()
                != VillagerProfession.FISHERMAN) return;

        ItemEntity catchEntity = resolveItem(level, fisher, CATCH_ID);
        if (catchEntity != null) {
            catchEntity.setDeltaMovement(fisher.position()
                    .subtract(catchEntity.position()).normalize()
                    .scale(0.18D).add(0.0D, 0.08D, 0.0D));
            if (gameTime >= fisher.getPersistentData().getLong(COLLECT_AT)
                    || fisher.distanceToSqr(catchEntity) <= 4.0D) {
                collectCatch(fisher, level, catchEntity, gameTime);
            }
            return;
        } else if (hasUuid(fisher, CATCH_ID)) {
            clearUuid(fisher, CATCH_ID);
        }

        ItemEntity hook = resolveItem(level, fisher, HOOK_ID);
        if (hook == null) {
            if (hasUuid(fisher, HOOK_ID)) clearUuid(fisher, HOOK_ID);
            return;
        }
        fisher.setItemInHand(Hand.MAIN_HAND,
                new ItemStack(Items.FISHING_ROD));
        maintainCast(fisher, level, hook, gameTime);
        renderLine(fisher, level, hook, gameTime);
        long biteAt = fisher.getPersistentData().getLong(BITE_AT);
        long reelAt = fisher.getPersistentData().getLong(REEL_AT);
        if (gameTime == biteAt) {
            hook.setDeltaMovement(0.0D, -0.10D, 0.0D);
            level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY(),
                    hook.getZ(), 8, 0.25D, 0.08D, 0.25D, 0.06D);
            level.playSound(null, entityBlockPos(hook),
                    SoundEvents.FISHING_BOBBER_SPLASH, SoundCategory.NEUTRAL,
                    0.8F, 0.9F + fisher.getRandom().nextFloat() * 0.2F);
        }
        if (gameTime >= reelAt) {
            reel(fisher, level, hook, gameTime);
        }
    }

    private static void maintainCast(VillagerEntity fisher, ServerWorld level,
                                     ItemEntity hook, long gameTime) {
        long castEnd = fisher.getPersistentData().getLong(CAST_END_AT);
        BlockPos water = readPos(fisher, WATER_TARGET);
        if (water == null || gameTime >= castEnd) {
            hook.setDeltaMovement(Vec3d.ZERO);
            if (water != null) {
                hook.setPos(water.getX() + 0.5D, water.getY() + 0.82D,
                        water.getZ() + 0.5D);
            }
            return;
        }
        Vec3d target = Minecraft115VectorCompat.atCenterOf(water).add(0.0D, 0.32D, 0.0D);
        Vec3d delta = target.subtract(hook.position());
        if (delta.lengthSqr() <= 0.36D) {
            hook.setPos(target.x, target.y, target.z);
            hook.setDeltaMovement(Vec3d.ZERO);
            fisher.getPersistentData().putLong(CAST_END_AT, gameTime);
            level.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH,
                    SoundCategory.NEUTRAL, 0.55F, 1.15F);
            return;
        }
        hook.setDeltaMovement(delta.normalize().scale(0.72D));
    }

    private static void renderLine(VillagerEntity fisher, ServerWorld level,
                                   ItemEntity hook, long gameTime) {
        if (!VanillaInstinctsScheduler.isScheduled(fisher,
                configuredTicks(ProfessionRules.FISHER_LINE_PARTICLE_TICKS))) return;
        Vec3d start = fisher.position().add(0.0D, 1.35D, 0.0D);
        Vec3d delta = hook.position().subtract(start);
        int points = Math.max(2, Math.min(10, (int) delta.length()));
        for (int index = 1; index < points; index++) {
            Vec3d point = start.add(delta.scale(index / (double) points));
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void cast(VillagerEntity fisher, ServerWorld level,
                             BlockPos water, long gameTime) {
        if (hasUuid(fisher, HOOK_ID) || !isFishable(level, water)) return;
        fisher.setItemInHand(Hand.MAIN_HAND,
                new ItemStack(Items.FISHING_ROD));
        fisher.swing(Hand.MAIN_HAND);
        Vec3d origin = fisher.position().add(0.0D, 1.35D, 0.0D);
        Vec3d target = Minecraft115VectorCompat.atCenterOf(water).add(0.0D, 0.32D, 0.0D);
        ItemEntity hook = new ItemEntity(level, origin.x, origin.y, origin.z,
                new ItemStack(Items.TRIPWIRE_HOOK));
        hook.setNoGravity(true);
        hook.setPickUpDelay(32_767);
        hook.setThrower(fisher.getUUID());
        hook.setDeltaMovement(target.subtract(origin).normalize().scale(0.72D));
        hook.getPersistentData().putBoolean(FISHER_VISUAL, true);
        hook.getPersistentData().putBoolean(
                VillagerFoodExchangeController.VILLAGE_ORIGIN, true);
        level.addFreshEntity(hook);
        fisher.getPersistentData().putUUID(HOOK_ID, hook.getUUID());
        fisher.getPersistentData().putLong(CAST_END_AT,
                gameTime + configuredTicks(ProfessionRules.FISHER_CAST_TRAVEL_TICKS));
        int wait = configuredTicks(ProfessionRules.FISHER_CAST_MIN_TICKS)
                + fisher.getRandom().nextInt(
                configuredTicks(ProfessionRules.FISHER_CAST_VARIATION_TICKS) + 1);
        long reelAt = gameTime + wait;
        fisher.getPersistentData().putLong(REEL_AT, reelAt);
        fisher.getPersistentData().putLong(BITE_AT, reelAt - 16L);
    }

    private static void reel(VillagerEntity fisher, ServerWorld level,
                             ItemEntity hook, long gameTime) {
        Vec3d origin = hook.position();
        hook.remove();
        clearUuid(fisher, HOOK_ID);
        fisher.swing(Hand.MAIN_HAND);
        ItemStack catchStack = catchFor(fisher);
        ItemEntity catchEntity = new ItemEntity(level, origin.x, origin.y,
                origin.z, catchStack);
        catchEntity.setPickUpDelay(32_767);
        catchEntity.setThrower(fisher.getUUID());
        catchEntity.getPersistentData().putBoolean(
                VillagerFoodExchangeController.VILLAGE_ORIGIN, true);
        catchEntity.setDeltaMovement(fisher.position().subtract(origin)
                .normalize().scale(0.22D).add(0.0D, 0.18D, 0.0D));
        level.addFreshEntity(catchEntity);
        fisher.getPersistentData().putUUID(CATCH_ID, catchEntity.getUUID());
        fisher.getPersistentData().putLong(COLLECT_AT, gameTime + 24L);
    }

    private static void collectCatch(VillagerEntity fisher, ServerWorld level,
                                     ItemEntity catchEntity, long gameTime) {
        ItemStack caught = catchEntity.getItem().copy();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                fisher.getX(), fisher.getY() + 1.0D, fisher.getZ(),
                4, 0.2D, 0.3D, 0.2D, 0.0D);
        fisher.playSound(SoundEvents.ITEM_PICKUP, 0.5F,
                0.9F + fisher.getRandom().nextFloat() * 0.2F);
        catchEntity.remove();
        clearUuid(fisher, CATCH_ID);
        VillageMarketController.recordProduction(level,
                entityBlockPos(fisher), caught);
        long sequence = fisher.getPersistentData().getLong(SEQUENCE);
        boolean socialRoute = Math.floorMod(sequence, 2L) == 0L;
        boolean represented = !socialRoute
                && RecoveredTradeController.addProducedOffer(fisher, caught, 1);
        if (!represented) {
            ItemStack social = caught.copy();
            ProfessionStockController.insert(fisher.getInventory(), social);
            if (!social.isEmpty()) fisher.spawnAtLocation(social);
            fisher.getInventory().setChanged();
        }
        fisher.setItemInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        fisher.getPersistentData().putLong(READY_AT,
                gameTime + configuredTicks(ProfessionRules.FISHER_RECAST_TICKS));
    }

    public static ItemStack catchFor(VillagerEntity fisher) {
        long sequence = fisher.getPersistentData().getLong(SEQUENCE) + 1L;
        fisher.getPersistentData().putLong(SEQUENCE, sequence);
        int seed = fisher.getUUID().hashCode() + Long.hashCode(sequence * 31L);
        Item item = CATCHES.get(Math.floorMod(seed, CATCHES.size()));
        return new ItemStack(item);
    }

    public static BlockPos findWater(ServerWorld level, VillagerEntity fisher) {
        BlockPos center = entityBlockPos(fisher);
        int cursor = Math.floorMod(fisher.getPersistentData()
                .getInt(SCAN_CURSOR), WATER_COLUMNS.length);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int columns = Math.min(VanillaInstinctsScheduler.precisionLimit(
                level, fisher, ProfessionRules.FISHER_SCAN_COLUMNS,
                PerformanceRules.MINIMUM_CANDIDATE_LIMIT),
                WATER_COLUMNS.length);
        for (int offset = 0; offset < columns; offset++) {
            int packed = WATER_COLUMNS[(cursor + offset)
                    % WATER_COLUMNS.length];
            int x = packed >> 16;
            int z = (short) packed;
            for (int y = -4; y <= 4; y++) {
                BlockPos candidate = center.offset(x, y, z);
                if (!isFishable(level, candidate)) continue;
                double distance = center.distSqr(candidate);
                if (distance < bestDistance) {
                    best = immutableBlockPos(candidate);
                    bestDistance = distance;
                }
            }
        }
        fisher.getPersistentData().putInt(SCAN_CURSOR,
                (cursor + columns) % WATER_COLUMNS.length);
        return best;
    }

    public static int waterScanChecks() {
        return ProfessionRules.FISHER_SCAN_COLUMNS * 9;
    }

    private static int[] waterColumns() {
        int radius = ProfessionRules.FISHER_SCAN_RADIUS;
        int width = radius * 2 + 1;
        Integer[] values = new Integer[width * width];
        int index = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                values[index++] = x << 16 | z & 0xFFFF;
            }
        }
        java.util.Arrays.sort(values, Comparator.comparingInt(value -> {
            int x = value >> 16;
            int z = (short) (int) value;
            return x * x + z * z;
        }));
        int[] result = new int[values.length];
        for (int slot = 0; slot < values.length; slot++) {
            result[slot] = values[slot];
        }
        return result;
    }

    public static boolean isFishable(ServerWorld level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().getType() == Fluids.WATER
                && state.getFluidState().isSource()
                && level.getBlockState(pos.above()).isAir();
    }

    private static Vec3d bankPosition(ServerWorld level, BlockPos water,
                                     VillagerEntity fisher) {
        return fr.vanillainstincts.compat.LegacyJava8.listOf(water.north(), water.south(), water.east(), water.west())
                .stream()
                .filter(pos -> level.getBlockState(pos).isAir()
                        && level.getBlockState(pos.above()).isAir()
                        && level.getBlockState(pos.below()).isSolidRender(
                        level, pos.below()))
                .min(Comparator.comparingDouble(pos ->
                        entityBlockPos(fisher).distSqr(pos)))
                .map(Minecraft115VectorCompat::atBottomCenterOf)
                .orElse(null);
    }

    private static BlockPos readPos(VillagerEntity villager, String key) {
        return villager.getPersistentData().contains(key)
                ? BlockPos.of(villager.getPersistentData().getLong(key)) : null;
    }

    private static boolean hasUuid(VillagerEntity villager, String key) {
        return villager.getPersistentData().hasUUID(key);
    }

    private static void clearUuid(VillagerEntity villager, String key) {
        villager.getPersistentData().remove(key);
    }

    private static ItemEntity resolveItem(ServerWorld level, VillagerEntity villager,
                                          String key) {
        if (!hasUuid(villager, key)) return null;
        Entity entity = level.getEntity(villager.getPersistentData().getUUID(key));
        return entity instanceof ItemEntity && ((ItemEntity) (entity)).isAlive() ? ((ItemEntity) (entity)) : null;
    }

    private static int configuredTicks(int baseTicks) {
        return RuntimeConfig.interval(FeatureFlag.FISHERMAN_ACTIVITY, baseTicks);
    }

}

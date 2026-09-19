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
import fr.vanillainstincts.compat.BlockPos;
import fr.vanillainstincts.compat.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import fr.vanillainstincts.compat.LegacyBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import fr.vanillainstincts.compat.Vec3;
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
            Items.fish, Items.fish, Items.fish, Items.fish,
            Items.fish, Items.fish, Items.bowl,
            Items.leather, Items.string,
            Items.name_tag, Items.saddle);
    private static final int[] WATER_COLUMNS = waterColumns();

    private FishermanController() {
    }

    public static boolean contribute(EntityVillager fisher,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     WorldServer level,
                                     long gameTime) {
        if (fisher == null || fisher.isChild() || fisher.isTrading()
                || state.danger(gameTime) != null
                || LegacyVillagerProfession.of(fisher)
                != LegacyVillagerProfession.FISHERMAN
                || VillagerRoutineController.phaseFor(level.getWorldTime())
                != VillagerSchedulePhase.WORK) {
            return false;
        }
        if (hasUuid(fisher, HOOK_ID) || hasUuid(fisher, CATCH_ID)) return true;
        if (gameTime < fisher.getEntityData().getLong(READY_AT)) return false;

        BlockPos water = readPos(fisher, WATER_TARGET);
        if (water == null || !isFishable(level, water)) {
            if (Math.floorMod(gameTime + fisher.getEntityId(),
                    configuredTicks(ProfessionRules.FISHER_SCAN_INTERVAL_TICKS)) != 0L) {
                return false;
            }
            water = findWater(level, fisher);
            if (water == null) {
                fisher.getEntityData().setLong(READY_AT,
                        gameTime + configuredTicks(ProfessionRules.FISHER_SCAN_INTERVAL_TICKS));
                return false;
            }
            fisher.getEntityData().setLong(WATER_TARGET, water.toLong());
        }

        Vec3 bank = bankPosition(level, water, fisher);
        if (bank == null) return false;
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(fr.vanillainstincts.compat.Minecraft17Compat.position(fisher), bank)
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

    public static void maintain(EntityVillager fisher, WorldServer level,
                                long gameTime) {
        if (fisher == null || LegacyVillagerProfession.of(fisher)
                != LegacyVillagerProfession.FISHERMAN) return;

        EntityItem catchEntity = resolveItem(level, fisher, CATCH_ID);
        if (catchEntity != null) {
            fr.vanillainstincts.compat.Minecraft112Compat.setMotion(catchEntity,
                    fr.vanillainstincts.compat.Minecraft112Compat.add(
                            fr.vanillainstincts.compat.Minecraft112Compat.scale(
                                    fr.vanillainstincts.compat.Minecraft17Compat.position(fisher).subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(catchEntity)).normalize(), 0.18D),
                            0.0D, 0.08D, 0.0D));
            if (gameTime >= fisher.getEntityData().getLong(COLLECT_AT)
                    || fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(fisher, catchEntity) <= 4.0D) {
                collectCatch(fisher, level, catchEntity, gameTime);
            }
            return;
        } else if (hasUuid(fisher, CATCH_ID)) {
            clearUuid(fisher, CATCH_ID);
        }

        EntityItem hook = resolveItem(level, fisher, HOOK_ID);
        if (hook == null) {
            if (hasUuid(fisher, HOOK_ID)) clearUuid(fisher, HOOK_ID);
            return;
        }
        fisher.setCurrentItemOrArmor(0,
                new ItemStack(Items.fishing_rod));
        maintainCast(fisher, level, hook, gameTime);
        renderLine(fisher, level, hook, gameTime);
        long biteAt = fisher.getEntityData().getLong(BITE_AT);
        long reelAt = fisher.getEntityData().getLong(REEL_AT);
        if (gameTime == biteAt) {
            fr.vanillainstincts.compat.Minecraft112Compat.setMotion(hook, 0.0D, -0.10D, 0.0D);
            fr.vanillainstincts.compat.Minecraft17Compat.spawnParticle(level, EnumParticleTypes.WATER_SPLASH, hook.posX, hook.posY,
                    hook.posZ, 8, 0.25D, 0.08D, 0.25D, 0.06D);
            fr.vanillainstincts.compat.Minecraft18SoundCompat.play(level, entityBlockPos(hook), "random.splash", 0.8F, 0.9F +fr.vanillainstincts.compat.Minecraft112Compat.random(fisher).nextFloat() * 0.2F);
        }
        if (gameTime >= reelAt) {
            reel(fisher, level, hook, gameTime);
        }
    }

    private static void maintainCast(EntityVillager fisher, WorldServer level,
                                     EntityItem hook, long gameTime) {
        long castEnd = fisher.getEntityData().getLong(CAST_END_AT);
        BlockPos water = readPos(fisher, WATER_TARGET);
        if (water == null || gameTime >= castEnd) {
            fr.vanillainstincts.compat.Minecraft112Compat.setMotion(hook, new Vec3(0.0D, 0.0D, 0.0D));
            if (water != null) {
                fr.vanillainstincts.compat.Minecraft112Compat.teleport(hook, water.getX() + 0.5D, water.getY() + 0.82D,
                        water.getZ() + 0.5D);
            }
            return;
        }
        Vec3 target =fr.vanillainstincts.compat.Minecraft112Compat.add(Minecraft115VectorCompat.atCenterOf(water), 0.0D, 0.32D, 0.0D);
        Vec3 delta = target.subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(hook));
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(delta) <= 0.36D) {
            fr.vanillainstincts.compat.Minecraft112Compat.teleport(hook, target.xCoord, target.yCoord, target.zCoord);
            fr.vanillainstincts.compat.Minecraft112Compat.setMotion(hook, new Vec3(0.0D, 0.0D, 0.0D));
            fisher.getEntityData().setLong(CAST_END_AT, gameTime);
            fr.vanillainstincts.compat.Minecraft18SoundCompat.play(level, water, "random.splash", 0.55F, 1.15F);
            return;
        }
        fr.vanillainstincts.compat.Minecraft112Compat.setMotion(hook, fr.vanillainstincts.compat.Minecraft112Compat.scale(delta.normalize(), 0.72D));
    }

    private static void renderLine(EntityVillager fisher, WorldServer level,
                                   EntityItem hook, long gameTime) {
        if (!VanillaInstinctsScheduler.isScheduled(fisher,
                configuredTicks(ProfessionRules.FISHER_LINE_PARTICLE_TICKS))) return;
        Vec3 start =fr.vanillainstincts.compat.Minecraft112Compat.add(fr.vanillainstincts.compat.Minecraft17Compat.position(fisher), 0.0D, 1.35D, 0.0D);
        Vec3 delta = fr.vanillainstincts.compat.Minecraft17Compat.position(hook).subtract(start);
        int points = Math.max(2, Math.min(10, (int) fr.vanillainstincts.compat.Minecraft112Compat.length(delta)));
        for (int index = 1; index < points; index++) {
            Vec3 point = start.add(fr.vanillainstincts.compat.Minecraft112Compat.scale(delta, index / (double) points));
            fr.vanillainstincts.compat.Minecraft17Compat.spawnParticle(level, EnumParticleTypes.CRIT, point.xCoord, point.yCoord, point.zCoord,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void cast(EntityVillager fisher, WorldServer level,
                             BlockPos water, long gameTime) {
        if (hasUuid(fisher, HOOK_ID) || !isFishable(level, water)) return;
        fisher.setCurrentItemOrArmor(0,
                new ItemStack(Items.fishing_rod));
        fisher.swingItem();
        Vec3 origin =fr.vanillainstincts.compat.Minecraft112Compat.add(fr.vanillainstincts.compat.Minecraft17Compat.position(fisher), 0.0D, 1.35D, 0.0D);
        Vec3 target =fr.vanillainstincts.compat.Minecraft112Compat.add(Minecraft115VectorCompat.atCenterOf(water), 0.0D, 0.32D, 0.0D);
        EntityItem hook = new EntityItem(level, origin.xCoord, origin.yCoord, origin.zCoord,
                new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.tripwire_hook)));
        fr.vanillainstincts.compat.Minecraft110Compat.setNoGravity(hook, true);
        fr.vanillainstincts.compat.Minecraft112Compat.setPickupDelay(hook, 32_767);
        hook.func_145797_a(fisher.getUniqueID().toString());
        fr.vanillainstincts.compat.Minecraft112Compat.setMotion(hook, fr.vanillainstincts.compat.Minecraft112Compat.scale(target.subtract(origin).normalize(), 0.72D));
        hook.getEntityData().setBoolean(FISHER_VISUAL, true);
        hook.getEntityData().setBoolean(
                VillagerFoodExchangeController.VILLAGE_ORIGIN, true);
        level.spawnEntityInWorld(hook);
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(fisher.getEntityData(), HOOK_ID, hook.getUniqueID());
        fisher.getEntityData().setLong(CAST_END_AT,
                gameTime + configuredTicks(ProfessionRules.FISHER_CAST_TRAVEL_TICKS));
        int wait = configuredTicks(ProfessionRules.FISHER_CAST_MIN_TICKS)
                +fr.vanillainstincts.compat.Minecraft112Compat.random(fisher).nextInt(
                configuredTicks(ProfessionRules.FISHER_CAST_VARIATION_TICKS) + 1);
        long reelAt = gameTime + wait;
        fisher.getEntityData().setLong(REEL_AT, reelAt);
        fisher.getEntityData().setLong(BITE_AT, reelAt - 16L);
    }

    private static void reel(EntityVillager fisher, WorldServer level,
                             EntityItem hook, long gameTime) {
        Vec3 origin = fr.vanillainstincts.compat.Minecraft17Compat.position(hook);
        fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(hook);
        clearUuid(fisher, HOOK_ID);
        fisher.swingItem();
        ItemStack catchStack = catchFor(fisher);
        EntityItem catchEntity = new EntityItem(level, origin.xCoord, origin.yCoord,
                origin.zCoord, catchStack);
        fr.vanillainstincts.compat.Minecraft112Compat.setPickupDelay(catchEntity, 32_767);
        catchEntity.func_145797_a(fisher.getUniqueID().toString());
        catchEntity.getEntityData().setBoolean(
                VillagerFoodExchangeController.VILLAGE_ORIGIN, true);
        fr.vanillainstincts.compat.Minecraft112Compat.setMotion(catchEntity,
                fr.vanillainstincts.compat.Minecraft112Compat.add(
                        fr.vanillainstincts.compat.Minecraft112Compat.scale(fr.vanillainstincts.compat.Minecraft17Compat.position(fisher).subtract(origin).normalize(), 0.22D),
                        0.0D, 0.18D, 0.0D));
        level.spawnEntityInWorld(catchEntity);
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(fisher.getEntityData(), CATCH_ID, catchEntity.getUniqueID());
        fisher.getEntityData().setLong(COLLECT_AT, gameTime + 24L);
    }

    private static void collectCatch(EntityVillager fisher, WorldServer level,
                                     EntityItem catchEntity, long gameTime) {
        ItemStack caught = catchEntity.getEntityItem().copy();
        fr.vanillainstincts.compat.Minecraft17Compat.spawnParticle(level, EnumParticleTypes.VILLAGER_HAPPY,
                fisher.posX, fisher.posY + 1.0D, fisher.posZ,
                4, 0.2D, 0.3D, 0.2D, 0.0D);
        fisher.playSound("random.pop", 0.5F,
                0.9F +fr.vanillainstincts.compat.Minecraft112Compat.random(fisher).nextFloat() * 0.2F);
        fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(catchEntity);
        clearUuid(fisher, CATCH_ID);
        VillageMarketController.recordProduction(level,
                entityBlockPos(fisher), caught);
        long sequence = fisher.getEntityData().getLong(SEQUENCE);
        boolean socialRoute = Math.floorMod(sequence, 2L) == 0L;
        boolean represented = !socialRoute
                && RecoveredTradeController.addProducedOffer(fisher, caught, 1);
        if (!represented) {
            ItemStack social = caught.copy();
            ProfessionStockController.insert(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(fisher), social);
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(social)) fr.vanillainstincts.compat.Minecraft112Compat.spawnItem(fisher, social);
            fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(fisher).markDirty();
        }
        fisher.setCurrentItemOrArmor(0, null);
        fisher.getEntityData().setLong(READY_AT,
                gameTime + configuredTicks(ProfessionRules.FISHER_RECAST_TICKS));
    }

    public static ItemStack catchFor(EntityVillager fisher) {
        long sequence = fisher.getEntityData().getLong(SEQUENCE) + 1L;
        fisher.getEntityData().setLong(SEQUENCE, sequence);
        int seed = fisher.getUniqueID().hashCode() + Long.hashCode(sequence * 31L);
        Item item = CATCHES.get(Math.floorMod(seed, CATCHES.size()));
        return new ItemStack(item);
    }

    public static BlockPos findWater(WorldServer level, EntityVillager fisher) {
        BlockPos center = entityBlockPos(fisher);
        int cursor = Math.floorMod(fisher.getEntityData()
                .getInteger(SCAN_CURSOR), WATER_COLUMNS.length);
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
                BlockPos candidate = fr.vanillainstincts.compat.Minecraft112Compat.offset(center, x, y, z);
                if (!isFishable(level, candidate)) continue;
                double distance = center.distanceSq(candidate);
                if (distance < bestDistance) {
                    best = immutableBlockPos(candidate);
                    bestDistance = distance;
                }
            }
        }
        fisher.getEntityData().setInteger(SCAN_CURSOR,
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

    public static boolean isFishable(WorldServer level, BlockPos pos) {
        if (level == null || pos == null || !fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos)) return false;
        LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos);
        return state.getBlock() == Blocks.water
                && fr.vanillainstincts.compat.Minecraft112Compat.isAir(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos.up()));
    }

    private static Vec3 bankPosition(WorldServer level, BlockPos water,
                                     EntityVillager fisher) {
        return fr.vanillainstincts.compat.LegacyJava8.listOf(water.north(), water.south(), water.east(), water.west())
                .stream()
                .filter(pos -> fr.vanillainstincts.compat.Minecraft112Compat.isAir(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos))
                        && fr.vanillainstincts.compat.Minecraft112Compat.isAir(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos.up()))
                        && fr.vanillainstincts.compat.Minecraft112Compat.isSolidRender(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos.down())))
                .min(Comparator.comparingDouble(pos ->
                        entityBlockPos(fisher).distanceSq(pos)))
                .map(Minecraft115VectorCompat::atBottomCenterOf)
                .orElse(null);
    }

    private static BlockPos readPos(EntityVillager villager, String key) {
        return villager.getEntityData().hasKey(key)
                ? BlockPos.fromLong(villager.getEntityData().getLong(key)) : null;
    }

    private static boolean hasUuid(EntityVillager villager, String key) {
        return fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(villager.getEntityData(), key);
    }

    private static void clearUuid(EntityVillager villager, String key) {
        villager.getEntityData().removeTag(key);
    }

    private static EntityItem resolveItem(WorldServer level, EntityVillager villager,
                                          String key) {
        if (!hasUuid(villager, key)) return null;
        Entity entity = fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(villager.getEntityData(), key));
        return entity instanceof EntityItem && ((EntityItem) (entity)).isEntityAlive() ? ((EntityItem) (entity)) : null;
    }

    private static int configuredTicks(int baseTicks) {
        return RuntimeConfig.interval(FeatureFlag.FISHERMAN_ACTIVITY, baseTicks);
    }

}

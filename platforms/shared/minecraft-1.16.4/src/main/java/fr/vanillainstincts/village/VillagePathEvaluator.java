package fr.vanillainstincts.village;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import fr.vanillainstincts.data.VillagePathPreferenceManager;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
/** Notation légère des positions de village sans remplacer le pathfinder. */
public final class VillagePathEvaluator {
    private VillagePathEvaluator() {
    }

    public static boolean isDangerous(ServerWorld level, BlockPos position) {
        return SafePositionFinder.isHazard(level, position)
                || level.getBlockState(position).is(
                        VanillaInstinctsTags.VILLAGER_DANGEROUS_BLOCKS);
    }

    public static double blockPreference(BlockState floorState) {
        if (floorState.is(VanillaInstinctsTags.VILLAGE_SAFE_PATHS)) {
            return 3.0D;
        }
        if (floorState.getBlock() instanceof DoorBlock) {
            return 1.5D;
        }
        return 0.0D;
    }

    /**
     * Profession-aware cost bias layered on top of vanilla pathfinding.
     * Datapacks own the static block costs; only truly dynamic world-state
     * bonuses (such as adjacent water) are evaluated here.
     */
    public static double professionPreference(VillagerEntity villager,
                                              ServerWorld level,
                                              BlockPos feet) {
        if (villager == null || level == null || feet == null) return 0.0D;
        VillagerProfession profession = villager.getVillagerData()
                .getProfession();
        BlockState floor = level.getBlockState(feet.below());
        BlockState through = level.getBlockState(feet);

        if (VillagePathPreferenceManager.ruleCount() == 0) {
            return legacyProfessionPreference(profession, level, feet, floor);
        }

        double score = VillagePathPreferenceManager.staticPreference(
                profession, through, floor);
        if (adjacentWater(level, feet)) {
            score += VillagePathPreferenceManager.adjacentWaterBonus(
                    profession);
        }
        score += VillagePathPreferenceManager.proximityPreference(
                profession, level, feet);
        return score;
    }

    private static double legacyProfessionPreference(
            VillagerProfession profession, ServerWorld level, BlockPos feet,
            BlockState floor) {
        double score = 0.0D;
        if (profession == VillagerProfession.FARMER) {
            if (floor.is(VanillaInstinctsTags.FARMER_PLANTABLE_ON)) score += 2.75D;
            if (floor.is(Blocks.GRASS_PATH)) score += 1.25D;
        } else if (floor.is(VanillaInstinctsTags.FARMER_PLANTABLE_ON)) {
            score -= 2.25D;
        }
        if (profession == VillagerProfession.FISHERMAN
                && adjacentWater(level, feet)) {
            score += 1.75D;
        }
        if (profession == VillagerProfession.MASON
                && (floor.is(Blocks.COBBLESTONE)
                || floor.is(Blocks.STONE)
                || floor.is(Blocks.STONE_BRICKS)
                || floor.is(Blocks.MOSSY_COBBLESTONE))) {
            score += 1.35D;
        }
        if ((profession == VillagerProfession.ARMORER
                || profession == VillagerProfession.TOOLSMITH
                || profession == VillagerProfession.WEAPONSMITH)
                && floor.is(Blocks.SMOOTH_STONE)) {
            score += 0.75D;
        }
        return score;
    }

    private static boolean adjacentWater(ServerWorld level, BlockPos feet) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getFluidState(feet.relative(direction))
                    .is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    public static int nearbyVillagerCount(ServerWorld level,
                                           VillagerEntity villager,
                                           double radius) {
        AxisAlignedBB area = villager.getBoundingBox().inflate(Math.max(0.5D, radius));
        return level.getEntitiesOfClass(VillagerEntity.class, area,
                other -> other != villager && other.isAlive()).size();
    }

    public static double score(VillagerEntity villager, ServerWorld level,
                               BlockPos feet, BlockPos objective) {
        Vector3d destination = Vector3d.atBottomCenterOf(feet);
        if (!SafePositionFinder.isSafeStandingPosition(villager, destination)) {
            return Double.NEGATIVE_INFINITY;
        }
        if (isDangerous(level, feet) || isDangerous(level, feet.below())) {
            return Double.NEGATIVE_INFINITY;
        }

        double distance = objective == null ? 0.0D
                : Math.sqrt(feet.distSqr(objective));
        double preference = blockPreference(level.getBlockState(feet.below()));
        if (FeatureGate.enabled(FeatureFlag.VILLAGER_PATH_PREFERENCES, level)) {
            preference += professionPreference(villager, level, feet);
        }
        int crowd = level.getEntitiesOfClass(VillagerEntity.class,
                new AxisAlignedBB(feet).inflate(VillageSocialRules.VILLAGER_CROWD_RADIUS),
                VillagerEntity::isAlive).size();
        double crowdPenalty = Math.max(0,
                crowd - VillageSocialRules.VILLAGER_COMFORTABLE_CROWD) * 1.75D;
        return preference - distance - crowdPenalty;
    }
}

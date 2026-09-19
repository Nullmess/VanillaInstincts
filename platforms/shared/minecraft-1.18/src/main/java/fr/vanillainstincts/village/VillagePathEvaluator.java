package fr.vanillainstincts.village;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import fr.vanillainstincts.data.VillagePathPreferenceManager;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
/** Notation légère des positions de village sans remplacer le pathfinder. */
public final class VillagePathEvaluator {
    private VillagePathEvaluator() {
    }

    public static boolean isDangerous(ServerLevel level, BlockPos position) {
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
    public static double professionPreference(Villager villager,
                                              ServerLevel level,
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
            VillagerProfession profession, ServerLevel level, BlockPos feet,
            BlockState floor) {
        double score = 0.0D;
        if (profession == VillagerProfession.FARMER) {
            if (floor.is(VanillaInstinctsTags.FARMER_PLANTABLE_ON)) score += 2.75D;
            if (floor.is(Blocks.DIRT_PATH)) score += 1.25D;
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

    private static boolean adjacentWater(ServerLevel level, BlockPos feet) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getFluidState(feet.relative(direction))
                    .is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    public static int nearbyVillagerCount(ServerLevel level,
                                           Villager villager,
                                           double radius) {
        AABB area = villager.getBoundingBox().inflate(Math.max(0.5D, radius));
        return level.getEntitiesOfClass(Villager.class, area,
                other -> other != villager && other.isAlive()).size();
    }

    public static double score(Villager villager, ServerLevel level,
                               BlockPos feet, BlockPos objective) {
        Vec3 destination = Vec3.atBottomCenterOf(feet);
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
        int crowd = level.getEntitiesOfClass(Villager.class,
                new AABB(feet).inflate(VillageSocialRules.VILLAGER_CROWD_RADIUS),
                Villager::isAlive).size();
        double crowdPenalty = Math.max(0,
                crowd - VillageSocialRules.VILLAGER_COMFORTABLE_CROWD) * 1.75D;
        return preference - distance - crowdPenalty;
    }
}

package fr.vanillainstincts.village;

import fr.vanillainstincts.compat.Minecraft115TagCompat;

import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import fr.vanillainstincts.data.VillagePathPreferenceManager;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.block.material.Material;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.init.Blocks;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
/** Notation légère des positions de village sans remplacer le pathfinder. */
public final class VillagePathEvaluator {
    private VillagePathEvaluator() {
    }

    public static boolean isDangerous(WorldServer level, BlockPos position) {
        return SafePositionFinder.isHazard(level, position)
                || Minecraft115TagCompat.blockStateIs(level.getBlockState(position),
                        VanillaInstinctsTags.VILLAGER_DANGEROUS_BLOCKS);
    }

    public static double blockPreference(IBlockState floorState) {
        if (Minecraft115TagCompat.blockStateIs(floorState, VanillaInstinctsTags.VILLAGE_SAFE_PATHS)) {
            return 3.0D;
        }
        if (floorState.getBlock() instanceof BlockDoor) {
            return 1.5D;
        }
        return 0.0D;
    }

    /**
     * Profession-aware cost bias layered on top of vanilla pathfinding.
     * Datapacks own the static block costs; only truly dynamic world-state
     * bonuses (such as adjacent water) are evaluated here.
     */
    public static double professionPreference(EntityVillager villager,
                                              WorldServer level,
                                              BlockPos feet) {
        if (villager == null || level == null || feet == null) return 0.0D;
        LegacyVillagerProfession profession = LegacyVillagerProfession.of(villager);
        IBlockState floor = level.getBlockState(feet.down());
        IBlockState through = level.getBlockState(feet);

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
            LegacyVillagerProfession profession, WorldServer level, BlockPos feet,
            IBlockState floor) {
        double score = 0.0D;
        if (profession == LegacyVillagerProfession.FARMER) {
            if (Minecraft115TagCompat.blockStateIs(floor, VanillaInstinctsTags.FARMER_PLANTABLE_ON)) score += 2.75D;
            if (floor.getBlock().equals(Blocks.GRASS_PATH)) score += 1.25D;
        } else if (Minecraft115TagCompat.blockStateIs(floor, VanillaInstinctsTags.FARMER_PLANTABLE_ON)) {
            score -= 2.25D;
        }
        if (profession == LegacyVillagerProfession.FISHERMAN
                && adjacentWater(level, feet)) {
            score += 1.75D;
        }
        if (profession == LegacyVillagerProfession.MASON
                && (floor.getBlock().equals(Blocks.COBBLESTONE)
                || floor.getBlock().equals(Blocks.STONE)
                || floor.getBlock().equals(Blocks.STONEBRICK)
                || floor.getBlock().equals(Blocks.MOSSY_COBBLESTONE))) {
            score += 1.35D;
        }
        if ((profession == LegacyVillagerProfession.ARMORER
                || profession == LegacyVillagerProfession.TOOLSMITH
                || profession == LegacyVillagerProfession.WEAPONSMITH)
                && floor.getBlock().equals(Blocks.STONE)) {
            score += 0.75D;
        }
        return score;
    }

    private static boolean adjacentWater(WorldServer level, BlockPos feet) {
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            if (level.getBlockState(feet.offset(direction)).getMaterial() == Material.WATER) {
                return true;
            }
        }
        return false;
    }

    public static int nearbyVillagerCount(WorldServer level,
                                           EntityVillager villager,
                                           double radius) {
        AxisAlignedBB area = villager.getEntityBoundingBox().expandXyz(Math.max(0.5D, radius));
        return level.getEntitiesWithinAABB(EntityVillager.class, area,
                other -> other != villager && other.isEntityAlive()).size();
    }

    public static double score(EntityVillager villager, WorldServer level,
                               BlockPos feet, BlockPos objective) {
        Vec3d destination = Minecraft115VectorCompat.atBottomCenterOf(feet);
        if (!SafePositionFinder.isSafeStandingPosition(villager, destination)) {
            return Double.NEGATIVE_INFINITY;
        }
        if (isDangerous(level, feet) || isDangerous(level, feet.down())) {
            return Double.NEGATIVE_INFINITY;
        }

        double distance = objective == null ? 0.0D
                : Math.sqrt(feet.distanceSq(objective));
        double preference = blockPreference(level.getBlockState(feet.down()));
        if (FeatureGate.enabled(FeatureFlag.VILLAGER_PATH_PREFERENCES, level)) {
            preference += professionPreference(villager, level, feet);
        }
        int crowd = level.getEntitiesWithinAABB(EntityVillager.class,
                new AxisAlignedBB(feet).expandXyz(VillageSocialRules.VILLAGER_CROWD_RADIUS),
                other -> other.isEntityAlive()).size();
        double crowdPenalty = Math.max(0,
                crowd - VillageSocialRules.VILLAGER_COMFORTABLE_CROWD) * 1.75D;
        return preference - distance - crowdPenalty;
    }
}

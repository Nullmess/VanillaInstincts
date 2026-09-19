package fr.vanillainstincts.village;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;

/** Protection des blocs. */
public final class VillageConstructionSafety {
    private VillageConstructionSafety() {
    }

    public static boolean canApply(WorldServer level, BlockPos position,
                                   IBlockState current, IBlockState expected,
                                   boolean terrainReplacement,
                                   boolean road) {
        if (level == null || position == null || current == null
                || expected == null) return false;
        if (current.equals(expected)) return true;
        if (level.getTileEntity(position) != null) return false;
        if (isProtected(current)) return false;

        if (road) {
            return fr.vanillainstincts.compat.Minecraft112Compat.isAir(current) || current.getMaterial().isReplaceable()
                    || VillageRoadPlanner.isPathSurface(level, position)
                    || isRoadGround(current);
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.isAir(expected)) {
            return fr.vanillainstincts.compat.Minecraft112Compat.isAir(current) || current.getMaterial().isReplaceable()
                    || isSoftNaturalObstruction(current);
        }
        if (terrainReplacement && isFoundationGround(current)
                && !VillageEvolutionController.isVillageArchitecture(current)) {
            return true;
        }
        return fr.vanillainstincts.compat.Minecraft112Compat.isAir(current) || current.getMaterial().isReplaceable()
                || isMissing(current)
                || current.getBlock() == expected.getBlock();
    }

    public static boolean conflicts(WorldServer level, BlockPos position,
                                    IBlockState current, IBlockState expected,
                                    boolean terrainReplacement,
                                    boolean road) {
        return !current.equals(expected) && !canApply(level, position, current,
                expected, terrainReplacement, road);
    }

    public static boolean isNaturalGround(IBlockState state) {
        return isRoadGround(state);
    }

    public static boolean isRoadGround(IBlockState state) {
        return state != null && (state.getBlock().equals(Blocks.GRASS)
                || state.getBlock().equals(Blocks.DIRT) || state.getBlock().equals(Blocks.DIRT)
                || state.getBlock().equals(Blocks.DIRT) || state.getBlock().equals(Blocks.DIRT)
                || state.getBlock().equals(Blocks.SAND) || state.getBlock().equals(Blocks.SAND)
                || state.getBlock().equals(Blocks.GRAVEL) || state.getBlock().equals(Blocks.SNOW)
                || state.getBlock().equals(Blocks.SNOW));
    }

    public static boolean isMissing(IBlockState state) {
        return state != null && (fr.vanillainstincts.compat.Minecraft112Compat.isAir(state) || state.getBlock().equals(Blocks.FIRE)
                || !!state.getMaterial().isLiquid());
    }

    private static boolean isFoundationGround(IBlockState state) {
        return isRoadGround(state);
    }

    private static boolean isSoftNaturalObstruction(IBlockState state) {
        return state.getBlock().equals(Blocks.SNOW) || state.getBlock().equals(Blocks.FIRE)
                || state.getBlock().equals(Blocks.GRASS) || state.getBlock().equals(Blocks.TALLGRASS)
                || state.getBlock().equals(Blocks.TALLGRASS) || state.getBlock().equals(Blocks.DOUBLE_PLANT)
                || state.getBlock().equals(Blocks.DEADBUSH) || state.getBlock().equals(Blocks.VINE)
               ;
    }

    private static boolean isProtected(IBlockState state) {
        return state.getBlock().equals(Blocks.BEDROCK) || state.getBlock().equals(Blocks.BARRIER)
                || state.getBlock().equals(Blocks.END_PORTAL_FRAME)
                || state.getBlock().equals(Blocks.END_PORTAL)
                || state.getBlock().equals(Blocks.PORTAL)
                || state.getBlock().equals(Blocks.COMMAND_BLOCK)
                || state.getBlock().equals(Blocks.CHAIN_COMMAND_BLOCK)
                || state.getBlock().equals(Blocks.REPEATING_COMMAND_BLOCK)
                || state.getBlock().equals(Blocks.STRUCTURE_BLOCK);
    }
}

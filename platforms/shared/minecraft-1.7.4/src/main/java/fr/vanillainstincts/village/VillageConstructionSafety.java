package fr.vanillainstincts.village;

import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.init.Blocks;
import fr.vanillainstincts.compat.LegacyBlockState;

/** Protection des blocs. */
public final class VillageConstructionSafety {
    private VillageConstructionSafety() {
    }

    public static boolean canApply(WorldServer level, BlockPos position,
                                   LegacyBlockState current, LegacyBlockState expected,
                                   boolean terrainReplacement,
                                   boolean road) {
        if (level == null || position == null || current == null
                || expected == null) return false;
        if (current.equals(expected)) return true;
        if (fr.vanillainstincts.compat.Minecraft17Compat.getTileEntity(level, position) != null) return false;
        if (isProtected(current)) return false;

        if (road) {
            return fr.vanillainstincts.compat.Minecraft112Compat.isAir(current) || current.getBlock().getMaterial().isReplaceable()
                    || VillageRoadPlanner.isPathSurface(level, position)
                    || isRoadGround(current);
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.isAir(expected)) {
            return fr.vanillainstincts.compat.Minecraft112Compat.isAir(current) || current.getBlock().getMaterial().isReplaceable()
                    || isSoftNaturalObstruction(current);
        }
        if (terrainReplacement && isFoundationGround(current)
                && !VillageEvolutionController.isVillageArchitecture(current)) {
            return true;
        }
        return fr.vanillainstincts.compat.Minecraft112Compat.isAir(current) || current.getBlock().getMaterial().isReplaceable()
                || isMissing(current)
                || current.getBlock() == expected.getBlock();
    }

    public static boolean conflicts(WorldServer level, BlockPos position,
                                    LegacyBlockState current, LegacyBlockState expected,
                                    boolean terrainReplacement,
                                    boolean road) {
        return !current.equals(expected) && !canApply(level, position, current,
                expected, terrainReplacement, road);
    }

    public static boolean isNaturalGround(LegacyBlockState state) {
        return isRoadGround(state);
    }

    public static boolean isRoadGround(LegacyBlockState state) {
        return state != null && (state.getBlock().equals(Blocks.grass)
                || state.getBlock().equals(Blocks.dirt) || state.getBlock().equals(Blocks.dirt)
                || state.getBlock().equals(Blocks.dirt) || state.getBlock().equals(Blocks.dirt)
                || state.getBlock().equals(Blocks.sand) || state.getBlock().equals(Blocks.sand)
                || state.getBlock().equals(Blocks.gravel) || state.getBlock().equals(Blocks.snow)
                || state.getBlock().equals(Blocks.snow));
    }

    public static boolean isMissing(LegacyBlockState state) {
        return state != null && (fr.vanillainstincts.compat.Minecraft112Compat.isAir(state) || state.getBlock().equals(Blocks.fire)
                || !!state.getBlock().getMaterial().isLiquid());
    }

    private static boolean isFoundationGround(LegacyBlockState state) {
        return isRoadGround(state);
    }

    private static boolean isSoftNaturalObstruction(LegacyBlockState state) {
        return state.getBlock().equals(Blocks.snow) || state.getBlock().equals(Blocks.fire)
                || state.getBlock().equals(Blocks.grass) || state.getBlock().equals(Blocks.tallgrass)
                || state.getBlock().equals(Blocks.tallgrass) || state.getBlock().equals(Blocks.double_plant)
                || state.getBlock().equals(Blocks.deadbush) || state.getBlock().equals(Blocks.vine)
               ;
    }

    private static boolean isProtected(LegacyBlockState state) {
        return state.getBlock().equals(Blocks.bedrock)
                || state.getBlock().equals(Blocks.end_portal_frame)
                || state.getBlock().equals(Blocks.end_portal)
                || state.getBlock().equals(Blocks.portal)
                || state.getBlock().equals(Blocks.command_block);
    }
}

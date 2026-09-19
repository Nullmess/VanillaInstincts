package fr.vanillainstincts.ai;

import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;

import fr.vanillainstincts.compat.Minecraft115TagCompat;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;
import fr.vanillainstincts.core.rules.CreeperRules;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeEventFactory;

/** Minecraft 1.12 passage-opening compatibility for creepers. */
public final class CreeperPassageController {
    private CreeperPassageController() {}

    public static Optional<BlockPos> findPassageObstacle(EntityCreeper creeper,
                                                         Vec3d destination) {
        if (creeper == null || destination == null
                || !(creeper.worldObj instanceof WorldServer)) {
            return Optional.empty();
        }
        WorldServer level = (WorldServer) creeper.worldObj;
        Vec3d current = creeper.getPositionVector();
        double dx = destination.xCoord - current.xCoord;
        double dz = destination.zCoord - current.zCoord;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-6D) return Optional.empty();
        dx /= length;
        dz /= length;
        BlockPos feet = new BlockPos(creeper.posX + dx * 0.9D,
                creeper.posY + 0.2D, creeper.posZ + dz * 0.9D);
        if (canOpenWithExplosion(level, creeper, feet)) return Optional.of(feet);
        BlockPos head = feet.up();
        return canOpenWithExplosion(level, creeper, head)
                ? Optional.of(head) : Optional.empty();
    }

    public static boolean canOpenWithExplosion(WorldServer level,
                                               EntityCreeper creeper,
                                               BlockPos pos) {
        if (level == null || creeper == null || pos == null
                || !level.getGameRules().getBoolean("mobGriefing")
                || !level.getGameRules().getBoolean("mobGriefing")
                || !level.isBlockLoaded(pos) || !validHeight(pos)
                || distanceSq(entityBlockPos(creeper), pos) > 9.0D) {
            return false;
        }
        IBlockState state = level.getBlockState(pos);
        if (state.getBlock().isAir(state, level, pos)
                || isOpenPassage(state)
                || Minecraft115TagCompat.blockStateIs(state, VanillaInstinctsTags.PROTECTED_BLOCKS)
                || level.getTileEntity(pos) != null
                || state.getMaterial().isLiquid()) {
            return false;
        }
        float hardness = state.getBlockHardness(level, pos);
        return hardness >= 0.0F
                && hardness <= CreeperRules.CREEPER_PASSAGE_MAX_HARDNESS;
    }

    public static boolean canIgnite(EntityCreeper creeper, Vec3d destination,
                                    SpeciesRuntimeState state,
                                    WorldServer level, long gameTime) {
        if (creeper == null || destination == null || state == null || level == null) {
            return false;
        }
        double distanceSqr = creeper.getPositionVector().squareDistanceTo(destination);
        return state.creeperPassageReady(gameTime)
                && state.creeperStalledSamples() >= CreeperRules.CREEPER_PASSAGE_STALL_SAMPLES
                && !creeper.hasIgnited()
                && distanceSqr >= CreeperRules.CREEPER_PASSAGE_MIN_TARGET_DISTANCE_SQR
                && distanceSqr <= CreeperRules.CREEPER_PASSAGE_MAX_TARGET_DISTANCE_SQR
                && level.getClosestPlayer(creeper.posX, creeper.posY, creeper.posZ,
                        CreeperRules.CREEPER_PASSAGE_PLAYER_SAFETY_RADIUS, false) == null
                && findPassageObstacle(creeper, destination)
                        .filter(pos -> opensTowardDestination(level, creeper, pos, destination))
                        .isPresent();
    }

    public static boolean opensTowardDestination(WorldServer level,
                                                 EntityCreeper creeper,
                                                 BlockPos obstacle,
                                                 Vec3d destination) {
        Vec3d current = creeper.getPositionVector();
        double dx = destination.xCoord - current.xCoord;
        double dz = destination.zCoord - current.zCoord;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-8D) return false;
        int stepX = axisStep(dx / length);
        int stepZ = axisStep(dz / length);
        if (stepX == 0 && stepZ == 0) return false;
        BlockPos beyond = obstacle.add(stepX, 0, stepZ);
        if (!level.isBlockLoaded(beyond) || !validHeight(beyond)) return false;
        boolean openFeet = level.getBlockState(beyond).getBlock().isPassable(level, beyond);
        boolean openHead = level.getBlockState(beyond.up()).getBlock().isPassable(level, beyond.up());
        return openFeet && openHead
                && Minecraft115VectorCompat.atCenterOf(beyond).squareDistanceTo(destination)
                < current.squareDistanceTo(destination);
    }

    private static boolean isOpenPassage(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockDoor && state.getPropertyNames().contains(BlockDoor.OPEN)) {
            return state.getValue(BlockDoor.OPEN);
        }
        if (block instanceof BlockFenceGate && state.getPropertyNames().contains(BlockFenceGate.OPEN)) {
            return state.getValue(BlockFenceGate.OPEN);
        }
        if (block instanceof BlockTrapDoor && state.getPropertyNames().contains(BlockTrapDoor.OPEN)) {
            return state.getValue(BlockTrapDoor.OPEN);
        }
        return false;
    }

    private static boolean validHeight(BlockPos pos) {
        return pos.getY() >= 0 && pos.getY() < 256;
    }

    private static double distanceSq(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static int axisStep(double component) {
        return component > 1.0E-6D ? 1 : component < -1.0E-6D ? -1 : 0;
    }

    public static boolean ignite(EntityCreeper creeper) {
        if (creeper == null || creeper.hasIgnited()) return false;
        creeper.ignite();
        return creeper.hasIgnited();
    }
}

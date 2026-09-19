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
import fr.vanillainstincts.compat.LegacyBlockState;
import net.minecraft.entity.monster.EntityCreeper;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeEventFactory;

/** Minecraft 1.12 passage-opening compatibility for creepers. */
public final class CreeperPassageController {
    private CreeperPassageController() {}

    public static Optional<BlockPos> findPassageObstacle(EntityCreeper creeper,
                                                         Vec3 destination) {
        if (creeper == null || destination == null
                || !(creeper.worldObj instanceof WorldServer)) {
            return Optional.empty();
        }
        WorldServer level = (WorldServer) creeper.worldObj;
        Vec3 current = fr.vanillainstincts.compat.Minecraft17Compat.position(creeper);
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
                || !level.getGameRules().getGameRuleBooleanValue("mobGriefing")
                || !level.getGameRules().getGameRuleBooleanValue("mobGriefing")
                || !fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos) || !validHeight(pos)
                || distanceSq(entityBlockPos(creeper), pos) > 9.0D) {
            return false;
        }
        LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos);
        if (fr.vanillainstincts.compat.Minecraft17Compat.isAir(level, pos)
                || isOpenPassage(state)
                || Minecraft115TagCompat.blockStateIs(state, VanillaInstinctsTags.PROTECTED_BLOCKS)
                || fr.vanillainstincts.compat.Minecraft17Compat.getTileEntity(level, pos) != null
                || state.getBlock().getMaterial().isLiquid()) {
            return false;
        }
        float hardness = state.getBlockHardness(level, pos);
        return hardness >= 0.0F
                && hardness <= CreeperRules.CREEPER_PASSAGE_MAX_HARDNESS;
    }

    public static boolean canIgnite(EntityCreeper creeper, Vec3 destination,
                                    SpeciesRuntimeState state,
                                    WorldServer level, long gameTime) {
        if (creeper == null || destination == null || state == null || level == null) {
            return false;
        }
        double distanceSqr = fr.vanillainstincts.compat.Minecraft17Compat.position(creeper).squareDistanceTo(destination);
        return state.creeperPassageReady(gameTime)
                && state.creeperStalledSamples() >= CreeperRules.CREEPER_PASSAGE_STALL_SAMPLES
                && !fr.vanillainstincts.compat.Minecraft17Compat.creeperIgnited(creeper)
                && distanceSqr >= CreeperRules.CREEPER_PASSAGE_MIN_TARGET_DISTANCE_SQR
                && distanceSqr <= CreeperRules.CREEPER_PASSAGE_MAX_TARGET_DISTANCE_SQR
                && level.getClosestPlayer(creeper.posX, creeper.posY, creeper.posZ,
                        CreeperRules.CREEPER_PASSAGE_PLAYER_SAFETY_RADIUS) == null
                && findPassageObstacle(creeper, destination)
                        .filter(pos -> opensTowardDestination(level, creeper, pos, destination))
                        .isPresent();
    }

    public static boolean opensTowardDestination(WorldServer level,
                                                 EntityCreeper creeper,
                                                 BlockPos obstacle,
                                                 Vec3 destination) {
        Vec3 current = fr.vanillainstincts.compat.Minecraft17Compat.position(creeper);
        double dx = destination.xCoord - current.xCoord;
        double dz = destination.zCoord - current.zCoord;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-8D) return false;
        int stepX = axisStep(dx / length);
        int stepZ = axisStep(dz / length);
        if (stepX == 0 && stepZ == 0) return false;
        BlockPos beyond = obstacle.add(stepX, 0, stepZ);
        if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, beyond) || !validHeight(beyond)) return false;
        boolean openFeet = fr.vanillainstincts.compat.Minecraft17Compat.isPassable(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, beyond).getBlock(), level, beyond);
        boolean openHead = fr.vanillainstincts.compat.Minecraft17Compat.isPassable(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, beyond.up()).getBlock(), level, beyond.up());
        return openFeet && openHead
                && Minecraft115VectorCompat.atCenterOf(beyond).squareDistanceTo(destination)
                < current.squareDistanceTo(destination);
    }

    private static boolean isOpenPassage(LegacyBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockDoor) {
            return fr.vanillainstincts.compat.Minecraft17Compat.doorOpen(state);
        }
        if (block instanceof BlockFenceGate) {
            return fr.vanillainstincts.compat.Minecraft17Compat.fenceGateOpen(state);
        }
        if (block instanceof BlockTrapDoor) {
            return fr.vanillainstincts.compat.Minecraft17Compat.trapDoorOpen(state);
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
        if (creeper == null || fr.vanillainstincts.compat.Minecraft17Compat.creeperIgnited(creeper)) return false;
        return fr.vanillainstincts.compat.Minecraft17Compat.igniteCreeper(creeper);
    }
}

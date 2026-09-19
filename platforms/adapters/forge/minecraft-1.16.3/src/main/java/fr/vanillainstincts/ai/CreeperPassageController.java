package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.rules.CreeperRules;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.world.GameRules;
import net.minecraft.block.BlockState;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.event.ForgeEventFactory;
/**
 * Détecte un véritable obstacle avant d'autoriser un creeper à utiliser son
 * explosion vanilla comme ouverture de passage.
 */
public final class CreeperPassageController {
    private CreeperPassageController() {
    }

    public static Optional<BlockPos> findPassageObstacle(CreeperEntity creeper,
                                                         Vector3d destination) {
        if (!(creeper.level instanceof ServerWorld)) {
            return Optional.empty();
        } ServerWorld level = (ServerWorld) (creeper.level);
        Vector3d horizontal = destination.subtract(creeper.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            return Optional.empty();
        }
        horizontal = horizontal.normalize();
        BlockPos feet = new BlockPos(
                creeper.getX() + horizontal.x * 0.9D,
                creeper.getY() + 0.2D,
                creeper.getZ() + horizontal.z * 0.9D);
        BlockPos head = feet.above();
        if (canOpenWithExplosion(level, creeper, feet)) {
            return Optional.of(feet);
        }
        if (canOpenWithExplosion(level, creeper, head)) {
            return Optional.of(head);
        }
        return Optional.empty();
    }

    public static boolean canOpenWithExplosion(ServerWorld level,
                                               CreeperEntity creeper,
                                               BlockPos pos) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !ForgeEventFactory.getMobGriefingEvent(level, creeper)
                || !level.isLoaded(pos)
                || !level.isInWorldBounds(pos)
                || creeper.blockPosition().distSqr(pos) > 9.0D) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()
                || state.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN)
                || state.is(VanillaInstinctsTags.PROTECTED_BLOCKS)
                || level.getBlockEntity(pos) != null
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0.0F
                && hardness <= CreeperRules.CREEPER_PASSAGE_MAX_HARDNESS;
    }

    public static boolean canIgnite(CreeperEntity creeper, Vector3d destination,
                                    SpeciesRuntimeState state,
                                    ServerWorld level, long gameTime) {
        double distanceSqr = creeper.position().distanceToSqr(destination);
        return state.creeperPassageReady(gameTime)
                && state.creeperStalledSamples()
                >= CreeperRules.CREEPER_PASSAGE_STALL_SAMPLES
                && !creeper.isIgnited()
                && distanceSqr >= CreeperRules.CREEPER_PASSAGE_MIN_TARGET_DISTANCE_SQR
                && distanceSqr <= CreeperRules.CREEPER_PASSAGE_MAX_TARGET_DISTANCE_SQR
                && !level.hasNearbyAlivePlayer(creeper.getX(), creeper.getY(),
                creeper.getZ(), CreeperRules.CREEPER_PASSAGE_PLAYER_SAFETY_RADIUS)
                && findPassageObstacle(creeper, destination)
                .filter(pos -> opensTowardDestination(level, creeper, pos,
                        destination)).isPresent();
    }

    public static boolean opensTowardDestination(ServerWorld level,
                                                CreeperEntity creeper,
                                                BlockPos obstacle,
                                                Vector3d destination) {
        Vector3d direction = destination.subtract(creeper.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 1.0E-8D) return false;
        direction = direction.normalize();
        int stepX = axisStep(direction.x);
        int stepZ = axisStep(direction.z);
        if (stepX == 0 && stepZ == 0) return false;
        BlockPos beyond = obstacle.offset(stepX, 0, stepZ);
        if (!level.isLoaded(beyond)) return false;
        boolean openFeet = level.getBlockState(beyond)
                .getCollisionShape(level, beyond).isEmpty();
        boolean openHead = level.getBlockState(beyond.above())
                .getCollisionShape(level, beyond.above()).isEmpty();
        return openFeet && openHead
                && Vector3d.atCenterOf(beyond).distanceToSqr(destination)
                < creeper.position().distanceToSqr(destination);
    }

    private static int axisStep(double component) {
        if (component > 1.0E-6D) return 1;
        if (component < -1.0E-6D) return -1;
        return 0;
    }

    public static boolean ignite(CreeperEntity creeper) {
        if (creeper.isIgnited()) {
            return false;
        }
        creeper.ignite();
        return creeper.isIgnited();
    }
}

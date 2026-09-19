package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.CreeperEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
/**
 * Classement léger de l'espace autour d'un creeper ou d'une cible.
 */
public final class CreeperEnvironmentEvaluator {
    private CreeperEnvironmentEvaluator() {
    }

    public static CreeperEnvironment classify(ServerLevel level,
                                              BlockPos position) {
        if (level == null || position == null || !level.isLoaded(position)) {
            return CreeperEnvironment.SHELTERED;
        }
        int solidSides = 0;
        BlockPos eye = position.above();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!level.getBlockState(eye.relative(direction))
                    .getCollisionShape(level, eye.relative(direction))
                    .isEmpty()) {
                solidSides++;
            }
        }
        boolean roof = !level.getBlockState(eye.above())
                .getCollisionShape(level, eye.above()).isEmpty();
        boolean sky = level.canSeeSky(eye.above());

        if (solidSides >= 3 || (roof && solidSides >= 2)) {
            return CreeperEnvironment.CONFINED;
        }
        if (roof || !sky || solidSides >= 1) {
            return CreeperEnvironment.SHELTERED;
        }
        return CreeperEnvironment.OPEN;
    }

    public static double ambushScore(CreeperEnvironment environment,
                                     boolean candidateVisible) {
        CreeperEnvironment normalized = environment == null
                ? CreeperEnvironment.SHELTERED : environment;
        return switch (normalized) {
            case OPEN -> candidateVisible ? -5.0D : 4.0D;
            case SHELTERED -> candidateVisible ? -2.0D : 7.0D;
            case CONFINED -> candidateVisible ? 1.0D : 5.0D;
        };
    }

    public static double stalkOffset(CreeperEnvironment environment) {
        return switch (environment == null
                ? CreeperEnvironment.SHELTERED : environment) {
            case OPEN -> 3.6D;
            case SHELTERED -> 2.8D;
            case CONFINED -> 2.1D;
        };
    }
}

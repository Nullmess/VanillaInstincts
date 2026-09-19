package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.CreeperEnvironment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.world.server.ServerWorld;
/**
 * Classement léger de l'espace autour d'un creeper ou d'une cible.
 */
public final class CreeperEnvironmentEvaluator {
    private CreeperEnvironmentEvaluator() {
    }

    public static CreeperEnvironment classify(ServerWorld level,
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
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((normalized)) { case OPEN:  return candidateVisible ? -5.0D : 4.0D; case SHELTERED:  return candidateVisible ? -2.0D : 7.0D; case CONFINED:  return candidateVisible ? 1.0D : 5.0D;  default: throw new AssertionError("Unexpected switch value"); } });
    }

    public static double stalkOffset(CreeperEnvironment environment) {
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((environment == null
                ? CreeperEnvironment.SHELTERED : environment)) { case OPEN:  return 3.6D; case SHELTERED:  return 2.8D; case CONFINED:  return 2.1D;  default: throw new AssertionError("Unexpected switch value"); } });
    }
}

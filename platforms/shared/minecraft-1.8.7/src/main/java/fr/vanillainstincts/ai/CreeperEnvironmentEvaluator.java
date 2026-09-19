package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.CreeperEnvironment;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.WorldServer;
/**
 * Classement léger de l'espace autour d'un creeper ou d'une cible.
 */
public final class CreeperEnvironmentEvaluator {
    private CreeperEnvironmentEvaluator() {
    }

    public static CreeperEnvironment classify(WorldServer level,
                                              BlockPos position) {
        if (level == null || position == null || !level.isBlockLoaded(position)) {
            return CreeperEnvironment.SHELTERED;
        }
        int solidSides = 0;
        BlockPos eye = position.up();
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            if (fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(eye.offset(direction)), level, eye.offset(direction))) {
                solidSides++;
            }
        }
        boolean roof = fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(eye.up()), level, eye.up());
        boolean sky = level.canSeeSky(eye.up());

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

package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import net.minecraft.util.math.vector.Vector3d;
/**
 * Choix déterministe d'une transition sol, mur, plafond ou descente.
 */
public final class SpiderSurfacePlanner {
    private SpiderSurfacePlanner() {
    }

    public static SpiderSurfaceMode chooseMode(boolean wallContact,
                                               boolean ceilingContact,
                                               double verticalDifference) {
        if (ceilingContact && Math.abs(verticalDifference) < 4.5D) {
            return SpiderSurfaceMode.CEILING;
        }
        if (wallContact && verticalDifference > 0.35D) {
            return SpiderSurfaceMode.WALL;
        }
        if ((wallContact || ceilingContact)
                && verticalDifference < -1.25D) {
            return SpiderSurfaceMode.DESCENDING;
        }
        return SpiderSurfaceMode.GROUND;
    }

    public static Vector3d directionAlongSurface(Vector3d destination,
                                             Vector3d current) {
        if (destination == null || current == null) {
            return Vector3d.ZERO;
        }
        Vector3d horizontal = destination.subtract(current)
                .multiply(1.0D, 0.0D, 1.0D);
        return horizontal.lengthSqr() < 1.0E-8D
                ? Vector3d.ZERO : horizontal.normalize();
    }
}

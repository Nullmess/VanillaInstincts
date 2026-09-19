package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import fr.vanillainstincts.compat.Vec3;
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

    public static Vec3 directionAlongSurface(Vec3 destination,
                                             Vec3 current) {
        if (destination == null || current == null) {
            return new Vec3(0.0D, 0.0D, 0.0D);
        }
        Vec3 horizontal = fr.vanillainstincts.compat.Minecraft112Compat.multiply(destination.subtract(current), 1.0D, 0.0D, 1.0D);
        return fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 0.0D) : horizontal.normalize();
    }
}

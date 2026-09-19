package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import net.minecraft.util.math.Vec3d;
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

    public static Vec3d directionAlongSurface(Vec3d destination,
                                             Vec3d current) {
        if (destination == null || current == null) {
            return Vec3d.ZERO;
        }
        Vec3d horizontal = fr.vanillainstincts.compat.Minecraft112Compat.multiply(destination.subtract(current), 1.0D, 0.0D, 1.0D);
        return fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D
                ? Vec3d.ZERO : horizontal.normalize();
    }
}

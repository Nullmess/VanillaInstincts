package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A traversable spider position plus the neighbouring block that supports it.
 *
 * <p>The {@code feet} position is always an air/passable cell. {@code support}
 * points from that cell to the solid surface used by the spider: DOWN for a
 * floor, UP for a ceiling, or a horizontal direction for a wall.</p>
 */
public record SpiderSurfaceNode(BlockPos feet, Direction support) {
    public SpiderSurfaceNode {
        Objects.requireNonNull(feet, "feet");
        Objects.requireNonNull(support, "support");
        feet = feet.immutable();
    }

    public SpiderSurfaceMode mode() {
        if (support == Direction.UP) {
            return SpiderSurfaceMode.CEILING;
        }
        if (support == Direction.DOWN) {
            return SpiderSurfaceMode.GROUND;
        }
        return SpiderSurfaceMode.WALL;
    }
}

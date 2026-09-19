package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import java.util.Objects;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * A traversable spider position plus the neighbouring block that supports it.
 *
 * <p>The {@code feet} position is always an air/passable cell. {@code support}
 * points from that cell to the solid surface used by the spider: DOWN for a
 * floor, UP for a ceiling, or a horizontal direction for a wall.</p>
 */
public class SpiderSurfaceNode {
    private final BlockPos feet;
    private final EnumFacing support;

    public BlockPos feet() { return this.feet; }

    public EnumFacing support() { return this.support; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SpiderSurfaceNode)) return false;
        SpiderSurfaceNode that = (SpiderSurfaceNode) other;
        return java.util.Objects.equals(this.feet, that.feet) && java.util.Objects.equals(this.support, that.support);
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.feet, this.support); }

    @Override
    public String toString() {
        return "SpiderSurfaceNode[" + "feet=" + this.feet + ", " + "support=" + this.support + "]";
    }

    public SpiderSurfaceNode(BlockPos feet, EnumFacing support) {
        Objects.requireNonNull(feet, "feet");
        Objects.requireNonNull(support, "support");
        feet = immutableBlockPos(feet);
    
        this.feet = feet;
        this.support = support;
    }

    public SpiderSurfaceMode mode() {
        if (support == EnumFacing.UP) {
            return SpiderSurfaceMode.CEILING;
        }
        if (support == EnumFacing.DOWN) {
            return SpiderSurfaceMode.GROUND;
        }
        return SpiderSurfaceMode.WALL;
    }
}

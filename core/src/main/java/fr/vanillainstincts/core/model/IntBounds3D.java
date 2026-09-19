package fr.vanillainstincts.core.model;

/** Inclusive integer bounds independent from Minecraft coordinates. */
public record IntBounds3D(int minX, int minY, int minZ,
                          int maxX, int maxY, int maxZ) {
    public IntBounds3D {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("Invalid bounds");
        }
    }

    public boolean intersects(IntBounds3D other) {
        return horizontalIntersects(other)
                && minY <= other.maxY && maxY >= other.minY;
    }

    public boolean horizontalIntersects(IntBounds3D other) {
        return other != null
                && minX <= other.maxX && maxX >= other.minX
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }
}

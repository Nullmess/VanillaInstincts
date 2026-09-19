package fr.vanillainstincts.core.model;

import java.util.Objects;

/** Inclusive integer bounds independent from Minecraft coordinates. */
public final class IntBounds3D {
    private final int minX, minY, minZ, maxX, maxY, maxZ;

    public IntBounds3D(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (maxX < minX || maxY < minY || maxZ < minZ) throw new IllegalArgumentException("Invalid bounds");
        this.minX=minX; this.minY=minY; this.minZ=minZ; this.maxX=maxX; this.maxY=maxY; this.maxZ=maxZ;
    }
    public int minX(){return minX;} public int minY(){return minY;} public int minZ(){return minZ;}
    public int maxX(){return maxX;} public int maxY(){return maxY;} public int maxZ(){return maxZ;}
    public boolean intersects(IntBounds3D other) { return horizontalIntersects(other) && minY <= other.maxY && maxY >= other.minY; }
    public boolean horizontalIntersects(IntBounds3D other) { return other != null && minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ; }
    @Override public boolean equals(Object o){ if(this==o)return true; if(!(o instanceof IntBounds3D))return false; IntBounds3D x=(IntBounds3D)o; return minX==x.minX&&minY==x.minY&&minZ==x.minZ&&maxX==x.maxX&&maxY==x.maxY&&maxZ==x.maxZ; }
    @Override public int hashCode(){ return Objects.hash(minX,minY,minZ,maxX,maxY,maxZ); }
    @Override public String toString(){ return "IntBounds3D[minX="+minX+", minY="+minY+", minZ="+minZ+", maxX="+maxX+", maxY="+maxY+", maxZ="+maxZ+"]"; }
}

package fr.vanillainstincts.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.entity.Entity;

/**
 * Immutable BlockPos-style value object for Minecraft 1.7.x, whose vanilla
 * API still uses integer x/y/z coordinates instead of BlockPos.
 */
public class BlockPos extends ChunkCoordinates {
    public static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    public BlockPos(int x, int y, int z) { super(x, y, z); }
    public BlockPos(double x, double y, double z) {
        this(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }
    public BlockPos(ChunkCoordinates other) {
        this(other == null ? 0 : other.posX, other == null ? 0 : other.posY, other == null ? 0 : other.posZ);
    }
    public BlockPos(Entity entity) {
        this(entity == null ? 0.0D : entity.posX, entity == null ? 0.0D : entity.posY, entity == null ? 0.0D : entity.posZ);
    }
    public BlockPos(net.minecraft.util.Vec3 vector) {
        this(vector == null ? 0.0D : vector.xCoord, vector == null ? 0.0D : vector.yCoord, vector == null ? 0.0D : vector.zCoord);
    }

    public int getX() { return this.posX; }
    public int getY() { return this.posY; }
    public int getZ() { return this.posZ; }

    public BlockPos add(int x, int y, int z) { return new BlockPos(posX + x, posY + y, posZ + z); }
    public BlockPos add(double x, double y, double z) { return new BlockPos(posX + x, posY + y, posZ + z); }
    public BlockPos subtract(BlockPos other) { return other == null ? this : add(-other.posX, -other.posY, -other.posZ); }
    public BlockPos up() { return up(1); }
    public BlockPos up(int n) { return add(0, n, 0); }
    public BlockPos down() { return down(1); }
    public BlockPos down(int n) { return add(0, -n, 0); }
    public BlockPos north() { return north(1); }
    public BlockPos north(int n) { return add(0, 0, -n); }
    public BlockPos south() { return south(1); }
    public BlockPos south(int n) { return add(0, 0, n); }
    public BlockPos west() { return west(1); }
    public BlockPos west(int n) { return add(-n, 0, 0); }
    public BlockPos east() { return east(1); }
    public BlockPos east(int n) { return add(n, 0, 0); }
    public BlockPos offset(EnumFacing side) { return offset(side, 1); }
    public BlockPos offset(EnumFacing side, int n) {
        return side == null ? this : add(side.getFrontOffsetX() * n, side.getFrontOffsetY() * n, side.getFrontOffsetZ() * n);
    }

    public double distanceSq(BlockPos other) {
        if (other == null) return Double.MAX_VALUE;
        double dx = this.posX - other.posX;
        double dy = this.posY - other.posY;
        double dz = this.posZ - other.posZ;
        return dx * dx + dy * dy + dz * dz;
    }
    public double distanceSq(double x, double y, double z) {
        double dx = this.posX - x;
        double dy = this.posY - y;
        double dz = this.posZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public long toLong() {
        return ((long)(posX) & 0x3FFFFFFL) << 38
                | ((long)(posY) & 0xFFFL) << 26
                | ((long)(posZ) & 0x3FFFFFFL);
    }
    public static BlockPos fromLong(long packed) {
        int x = (int)(packed >> 38);
        int y = (int)((packed >> 26) & 0xFFFL);
        int z = (int)(packed << 38 >> 38);
        if (y >= 2048) y -= 4096;
        return new BlockPos(x, y, z);
    }

    public static Iterable<BlockPos> getAllInBox(BlockPos a, BlockPos b) {
        List<BlockPos> result = new ArrayList<BlockPos>();
        if (a == null || b == null) return result;
        int minX=Math.min(a.posX,b.posX), maxX=Math.max(a.posX,b.posX);
        int minY=Math.min(a.posY,b.posY), maxY=Math.max(a.posY,b.posY);
        int minZ=Math.min(a.posZ,b.posZ), maxZ=Math.max(a.posZ,b.posZ);
        for (int x=minX; x<=maxX; x++) for (int y=minY; y<=maxY; y++) for (int z=minZ; z<=maxZ; z++)
            result.add(new BlockPos(x,y,z));
        return result;
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BlockPos)) return false;
        BlockPos other=(BlockPos)obj;
        return posX==other.posX && posY==other.posY && posZ==other.posZ;
    }
    @Override public int hashCode() { return (posY + posZ * 31) * 31 + posX; }
    @Override public String toString() { return "BlockPos{"+posX+","+posY+","+posZ+"}"; }
}

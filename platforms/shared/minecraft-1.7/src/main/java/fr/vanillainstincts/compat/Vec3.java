package fr.vanillainstincts.compat;

/** Public, allocation-safe facade over the pooled/protected 1.7.x Vec3 API. */
public class Vec3 extends net.minecraft.util.Vec3 {
    private static final net.minecraft.util.Vec3Pool VI_POOL = new net.minecraft.util.Vec3Pool(-1, -1);
    public Vec3(double x, double y, double z) { super(VI_POOL, x, y, z); }
    public static Vec3 of(net.minecraft.util.Vec3 value) {
        return value == null ? new Vec3(0.0D, 0.0D, 0.0D) : new Vec3(value.xCoord, value.yCoord, value.zCoord);
    }
    public Vec3 add(net.minecraft.util.Vec3 other) {
        return other == null ? this : new Vec3(xCoord + other.xCoord, yCoord + other.yCoord, zCoord + other.zCoord);
    }
    public Vec3 addVector(double x, double y, double z) { return new Vec3(xCoord + x, yCoord + y, zCoord + z); }
    public Vec3 subtract(net.minecraft.util.Vec3 other) {
        return other == null ? this : new Vec3(xCoord - other.xCoord, yCoord - other.yCoord, zCoord - other.zCoord);
    }
    public Vec3 normalize() {
        double len = Math.sqrt(xCoord*xCoord + yCoord*yCoord + zCoord*zCoord);
        return len < 1.0E-4D ? new Vec3(0.0D,0.0D,0.0D) : new Vec3(xCoord/len,yCoord/len,zCoord/len);
    }
}

package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** Compact server-authoritative control state for the currently possessed mob. */
public class PossessionInputPayload {
    private final int flags;
    private final int yaw100;
    private final int pitch100;

    public PossessionInputPayload(int flags, int yaw100, int pitch100) {
        this.flags = flags;
        this.yaw100 = yaw100;
        this.pitch100 = pitch100;
    }

    public int flags() { return this.flags; }

    public int yaw100() { return this.yaw100; }

    public int pitch100() { return this.pitch100; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PossessionInputPayload)) return false;
        PossessionInputPayload that = (PossessionInputPayload) other;
        return this.flags == that.flags && this.yaw100 == that.yaw100 && this.pitch100 == that.pitch100;
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.flags, this.yaw100, this.pitch100); }

    @Override
    public String toString() {
        return "PossessionInputPayload[" + "flags=" + this.flags + ", " + "yaw100=" + this.yaw100 + ", " + "pitch100=" + this.pitch100 + "]";
    }

    public static final int FORWARD = 1;
    public static final int BACK = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int JUMP = 1 << 4;
    public static final int DESCEND = 1 << 5;
    public static final int SPRINT = 1 << 6;
    public static final int SNEAK = 1 << 7;
    public static final int ATTACK = 1 << 8;
    public static final int USE = 1 << 9;

    public static final ResourceLocation ID =
            VanillaInstincts.id("possession_input");

    public PossessionInputPayload(PacketBuffer buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public float yaw() {
        return yaw100 / 100.0F;
    }

    public float pitch() {
        return Math.max(-90.0F, Math.min(90.0F, pitch100 / 100.0F));
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public void write(PacketBuffer buffer) {
        buffer.writeVarInt(flags);
        buffer.writeVarInt(yaw100);
        buffer.writeVarInt(pitch100);
    }

    public ResourceLocation id() {
        return ID;
    }
}

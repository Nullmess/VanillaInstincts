package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Compact server-authoritative control state for the currently possessed mob. */
public record PossessionInputPayload(int flags, int yaw100, int pitch100) {
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

    public PossessionInputPayload(FriendlyByteBuf buffer) {
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

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(flags);
        buffer.writeVarInt(yaw100);
        buffer.writeVarInt(pitch100);
    }

    public ResourceLocation id() {
        return ID;
    }
}

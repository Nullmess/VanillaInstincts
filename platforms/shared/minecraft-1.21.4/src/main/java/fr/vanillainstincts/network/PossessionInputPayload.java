package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Compact server-authoritative control state for the currently possessed mob. */
public record PossessionInputPayload(int flags, int yaw100, int pitch100)
        implements CustomPacketPayload {
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

    public static final Type<PossessionInputPayload> TYPE = new Type<>(
            VanillaInstincts.id("possession_input"));

    public static final StreamCodec<ByteBuf, PossessionInputPayload>
            STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            PossessionInputPayload::flags,
            ByteBufCodecs.VAR_INT,
            PossessionInputPayload::yaw100,
            ByteBufCodecs.VAR_INT,
            PossessionInputPayload::pitch100,
            PossessionInputPayload::new);

    public float yaw() {
        return yaw100 / 100.0F;
    }

    public float pitch() {
        return Math.max(-90.0F, Math.min(90.0F, pitch100 / 100.0F));
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

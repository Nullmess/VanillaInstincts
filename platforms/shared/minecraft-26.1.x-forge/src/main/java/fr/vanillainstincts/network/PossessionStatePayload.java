package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Authoritative server-to-client possession state. */
public record PossessionStatePayload(boolean active, int entityId)
        implements CustomPacketPayload {
    public static final Type<PossessionStatePayload> TYPE = new Type<>(
            VanillaInstincts.id("possession_state"));

    public static final StreamCodec<ByteBuf, PossessionStatePayload>
            STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            PossessionStatePayload::active,
            ByteBufCodecs.VAR_INT,
            PossessionStatePayload::entityId,
            PossessionStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

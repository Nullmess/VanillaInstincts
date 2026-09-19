package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-to-client state used to extend the native merchant rank bar. */
public record GrandMasterProgressPayload(int containerId, int masterTrades,
                                         int masteredDays,
                                         boolean grandMaster)
        implements CustomPacketPayload {
    public static final Type<GrandMasterProgressPayload> TYPE = new Type<>(
            VanillaInstincts.id("grand_master_progress"));

    public static final StreamCodec<ByteBuf, GrandMasterProgressPayload>
            STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            GrandMasterProgressPayload::containerId,
            ByteBufCodecs.VAR_INT,
            GrandMasterProgressPayload::masterTrades,
            ByteBufCodecs.VAR_INT,
            GrandMasterProgressPayload::masteredDays,
            ByteBufCodecs.BOOL,
            GrandMasterProgressPayload::grandMaster,
            GrandMasterProgressPayload::new);

    public GrandMasterProgressPayload {
        containerId = Math.max(0, containerId);
        masterTrades = Math.max(0, masterTrades);
        masteredDays = Math.max(0, masteredDays);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Edge-triggered actions while controlling a mob. */
public record PossessionActionPayload(int action) implements CustomPacketPayload {
    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;
    public static final int INVENTORY = 2; // legacy no-op: E now uses vanilla InventoryMenu
    public static final int DROP_ONE = 3;
    public static final int DROP_STACK = 4;
    public static final int SWAP_OFFHAND = 5;
    public static final int SELECT_SLOT_BASE = 10;

    public static int selectSlot(int slot) {
        return SELECT_SLOT_BASE + Math.max(0, Math.min(8, slot));
    }

    public static final Type<PossessionActionPayload> TYPE = new Type<>(
            VanillaInstincts.id("possession_action"));

    public static final StreamCodec<ByteBuf, PossessionActionPayload>
            STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            PossessionActionPayload::action,
            PossessionActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

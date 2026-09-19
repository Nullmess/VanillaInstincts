package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Edge-triggered actions while controlling a mob. */
public record PossessionActionPayload(int action) implements CustomPacketPayload {
    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;
    public static final int INVENTORY = 2; // legacy no-op: E now uses vanilla InventoryMenu
    public static final int DROP_ONE = 3;
    public static final int DROP_STACK = 4;
    public static final int SWAP_OFFHAND = 5;
    public static final int SELECT_SLOT_BASE = 10;

    public static final ResourceLocation ID =
            VanillaInstincts.id("possession_action");

    public PossessionActionPayload(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public static int selectSlot(int slot) {
        return SELECT_SLOT_BASE + Math.max(0, Math.min(8, slot));
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(action);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}

package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** Edge-triggered actions while controlling a mob. */
public class PossessionActionPayload {
    public PossessionActionPayload(int action) {
        this.action = action;
    }

    private final int action;

    public int action() { return this.action; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PossessionActionPayload)) return false;
        PossessionActionPayload that = (PossessionActionPayload) other;
        return this.action == that.action;
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.action); }

    @Override
    public String toString() {
        return "PossessionActionPayload[" + "action=" + this.action + "]";
    }

    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;
    public static final int INVENTORY = 2; // legacy no-op: E now uses vanilla InventoryMenu
    public static final int DROP_ONE = 3;
    public static final int DROP_STACK = 4;
    public static final int SWAP_OFFHAND = 5;
    public static final int SELECT_SLOT_BASE = 10;

    public static final ResourceLocation ID =
            VanillaInstincts.id("possession_action");

    public PossessionActionPayload(PacketBuffer buffer) {
        this(buffer.readVarInt());
    }

    public static int selectSlot(int slot) {
        return SELECT_SLOT_BASE + Math.max(0, Math.min(8, slot));
    }

    public void write(PacketBuffer buffer) {
        buffer.writeVarInt(action);
    }

    public ResourceLocation id() {
        return ID;
    }
}

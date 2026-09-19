package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** Authoritative server-to-client possession state. */
public class PossessionStatePayload {
    private final boolean active;
    private final int entityId;

    public PossessionStatePayload(boolean active, int entityId) {
        this.active = active;
        this.entityId = entityId;
    }

    public boolean active() { return this.active; }

    public int entityId() { return this.entityId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PossessionStatePayload)) return false;
        PossessionStatePayload that = (PossessionStatePayload) other;
        return this.active == that.active && this.entityId == that.entityId;
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.active, this.entityId); }

    @Override
    public String toString() {
        return "PossessionStatePayload[" + "active=" + this.active + ", " + "entityId=" + this.entityId + "]";
    }

    public static final ResourceLocation ID =
            VanillaInstincts.id("possession_state");

    public PossessionStatePayload(PacketBuffer buffer) {
        this(buffer.readBoolean(), buffer.readVarInt());
    }

    public void write(PacketBuffer buffer) {
        buffer.writeBoolean(active);
        buffer.writeVarInt(entityId);
    }

    public ResourceLocation id() {
        return ID;
    }
}

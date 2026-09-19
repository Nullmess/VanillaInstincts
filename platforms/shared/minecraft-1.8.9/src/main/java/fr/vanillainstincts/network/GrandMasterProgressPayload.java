package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** Server-to-client state used to extend the native merchant rank bar. */
public class GrandMasterProgressPayload {
    private final int containerId;
    private final int masterTrades;
    private final int masteredDays;
    private final boolean grandMaster;

    public int containerId() { return this.containerId; }

    public int masterTrades() { return this.masterTrades; }

    public int masteredDays() { return this.masteredDays; }

    public boolean grandMaster() { return this.grandMaster; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GrandMasterProgressPayload)) return false;
        GrandMasterProgressPayload that = (GrandMasterProgressPayload) other;
        return this.containerId == that.containerId && this.masterTrades == that.masterTrades && this.masteredDays == that.masteredDays && this.grandMaster == that.grandMaster;
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.containerId, this.masterTrades, this.masteredDays, this.grandMaster); }

    @Override
    public String toString() {
        return "GrandMasterProgressPayload[" + "containerId=" + this.containerId + ", " + "masterTrades=" + this.masterTrades + ", " + "masteredDays=" + this.masteredDays + ", " + "grandMaster=" + this.grandMaster + "]";
    }

    public static final ResourceLocation ID =
            VanillaInstincts.id("grand_master_progress");

    public GrandMasterProgressPayload(PacketBuffer buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean());
    }

    public GrandMasterProgressPayload(int containerId, int masterTrades, int masteredDays, boolean grandMaster) {
        containerId = Math.max(0, containerId);
        masterTrades = Math.max(0, masterTrades);
        masteredDays = Math.max(0, masteredDays);
    
        this.containerId = containerId;
        this.masterTrades = masterTrades;
        this.masteredDays = masteredDays;
        this.grandMaster = grandMaster;
    }

    public void write(PacketBuffer buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(masterTrades);
        buffer.writeVarInt(masteredDays);
        buffer.writeBoolean(grandMaster);
    }

    public ResourceLocation id() {
        return ID;
    }
}

package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client state used to extend the native merchant rank bar. */
public record GrandMasterProgressPayload(int containerId, int masterTrades,
                                         int masteredDays,
                                         boolean grandMaster) {
    public static final ResourceLocation ID =
            VanillaInstincts.id("grand_master_progress");

    public GrandMasterProgressPayload(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean());
    }

    public GrandMasterProgressPayload {
        containerId = Math.max(0, containerId);
        masterTrades = Math.max(0, masterTrades);
        masteredDays = Math.max(0, masteredDays);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(masterTrades);
        buffer.writeVarInt(masteredDays);
        buffer.writeBoolean(grandMaster);
    }

    public ResourceLocation id() {
        return ID;
    }
}

package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Authoritative server-to-client possession state. */
public record PossessionStatePayload(boolean active, int entityId)
        implements CustomPacketPayload {
    public static final ResourceLocation ID =
            VanillaInstincts.id("possession_state");

    public PossessionStatePayload(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readVarInt());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeVarInt(entityId);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}

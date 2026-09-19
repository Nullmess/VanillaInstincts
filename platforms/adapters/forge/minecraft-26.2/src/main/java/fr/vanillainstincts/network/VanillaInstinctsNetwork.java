package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/** Forge SimpleChannel bridge for the shared Vanilla Instincts payload model. */
public final class VanillaInstinctsNetwork {
    public static final int PROTOCOL = 2;
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(VanillaInstincts.id("main"))
            .networkProtocolVersion(PROTOCOL)
            .simpleChannel();

    private VanillaInstinctsNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(GrandMasterProgressPayload.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VanillaInstinctsNetwork::encodeGrandMaster)
                .decoder(VanillaInstinctsNetwork::decodeGrandMaster)
                .consumerMainThread(VanillaInstinctsNetwork::handleGrandMaster)
                .add();
        CHANNEL.messageBuilder(PossessionStatePayload.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VanillaInstinctsNetwork::encodePossessionState)
                .decoder(VanillaInstinctsNetwork::decodePossessionState)
                .consumerMainThread(VanillaInstinctsNetwork::handlePossessionState)
                .add();
        CHANNEL.messageBuilder(PossessionInputPayload.class, id++,
                        NetworkDirection.PLAY_TO_SERVER)
                .encoder(VanillaInstinctsNetwork::encodePossessionInput)
                .decoder(VanillaInstinctsNetwork::decodePossessionInput)
                .consumerMainThread(VanillaInstinctsNetwork::handlePossessionInput)
                .add();
        CHANNEL.messageBuilder(PossessionActionPayload.class, id,
                        NetworkDirection.PLAY_TO_SERVER)
                .encoder(VanillaInstinctsNetwork::encodePossessionAction)
                .decoder(VanillaInstinctsNetwork::decodePossessionAction)
                .consumerMainThread(VanillaInstinctsNetwork::handlePossessionAction)
                .add();
        CHANNEL.build();
    }

    public static void sendToServer(Object payload) {
        CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
    }

    public static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
    }

    private static void encodeGrandMaster(GrandMasterProgressPayload payload,
                                          FriendlyByteBuf buffer) {
        buffer.writeVarInt(payload.containerId());
        buffer.writeVarInt(payload.masterTrades());
        buffer.writeVarInt(payload.masteredDays());
        buffer.writeBoolean(payload.grandMaster());
    }

    private static GrandMasterProgressPayload decodeGrandMaster(
            FriendlyByteBuf buffer) {
        return new GrandMasterProgressPayload(buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
    }

    private static void handleGrandMaster(GrandMasterProgressPayload payload,
                                          CustomPayloadEvent.Context context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientPacketHandler.accept(payload);
        }
    }

    private static void encodePossessionState(PossessionStatePayload payload,
                                              FriendlyByteBuf buffer) {
        buffer.writeBoolean(payload.active());
        buffer.writeVarInt(payload.entityId());
    }

    private static PossessionStatePayload decodePossessionState(
            FriendlyByteBuf buffer) {
        return new PossessionStatePayload(buffer.readBoolean(),
                buffer.readVarInt());
    }

    private static void handlePossessionState(PossessionStatePayload payload,
                                              CustomPayloadEvent.Context context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientPacketHandler.accept(payload);
        }
    }

    private static void encodePossessionInput(PossessionInputPayload payload,
                                              FriendlyByteBuf buffer) {
        buffer.writeVarInt(payload.flags());
        buffer.writeVarInt(payload.yaw100());
        buffer.writeVarInt(payload.pitch100());
    }

    private static PossessionInputPayload decodePossessionInput(
            FriendlyByteBuf buffer) {
        return new PossessionInputPayload(buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt());
    }

    private static void handlePossessionInput(PossessionInputPayload payload,
                                              CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null) {
            MobPossessionManager.acceptInput(player, payload);
        }
    }

    private static void encodePossessionAction(PossessionActionPayload payload,
                                               FriendlyByteBuf buffer) {
        buffer.writeVarInt(payload.action());
    }

    private static PossessionActionPayload decodePossessionAction(
            FriendlyByteBuf buffer) {
        return new PossessionActionPayload(buffer.readVarInt());
    }

    private static void handlePossessionAction(PossessionActionPayload payload,
                                               CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null) {
            MobPossessionManager.action(player, payload.action());
        }
    }
}

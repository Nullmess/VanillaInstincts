package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.client.GrandMasterMerchantClientState;
import fr.vanillainstincts.client.MobPossessionClientState;
import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.simple.SimpleChannel;


/**
 * NeoForge 20.2 SimpleChannel bridge.
 *
 * <p>The payload registrar API used by NeoForge 20.4.70+ does not exist in
 * NeoForge 20.2, so this target keeps the legacy channel implementation while
 * reusing the same shared payload records.</p>
 */
public final class VanillaInstinctsNetwork {
    private static final String PROTOCOL = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(VanillaInstincts.id("main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private VanillaInstinctsNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(GrandMasterProgressPayload.class, id++)
                .encoder(VanillaInstinctsNetwork::encodeGrandMaster)
                .decoder(VanillaInstinctsNetwork::decodeGrandMaster)
                .consumerMainThread(VanillaInstinctsNetwork::handleGrandMaster)
                .add();
        CHANNEL.messageBuilder(PossessionStatePayload.class, id++)
                .encoder(VanillaInstinctsNetwork::encodePossessionState)
                .decoder(VanillaInstinctsNetwork::decodePossessionState)
                .consumerMainThread(VanillaInstinctsNetwork::handlePossessionState)
                .add();
        CHANNEL.messageBuilder(PossessionInputPayload.class, id++)
                .encoder(VanillaInstinctsNetwork::encodePossessionInput)
                .decoder(VanillaInstinctsNetwork::decodePossessionInput)
                .consumerMainThread(VanillaInstinctsNetwork::handlePossessionInput)
                .add();
        CHANNEL.messageBuilder(PossessionActionPayload.class, id)
                .encoder(VanillaInstinctsNetwork::encodePossessionAction)
                .decoder(VanillaInstinctsNetwork::decodePossessionAction)
                .consumerMainThread(VanillaInstinctsNetwork::handlePossessionAction)
                .add();
    }

    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    private static void encodeGrandMaster(GrandMasterProgressPayload payload,
                                          FriendlyByteBuf buffer) {
        payload.write(buffer);
    }

    private static GrandMasterProgressPayload decodeGrandMaster(
            FriendlyByteBuf buffer) {
        return new GrandMasterProgressPayload(buffer);
    }

    private static void handleGrandMaster(GrandMasterProgressPayload payload,
                                          NetworkEvent.Context context) {
        if (context.getSender() == null) {
            GrandMasterMerchantClientState.accept(payload);
        }
        context.setPacketHandled(true);
    }

    private static void encodePossessionState(PossessionStatePayload payload,
                                              FriendlyByteBuf buffer) {
        payload.write(buffer);
    }

    private static PossessionStatePayload decodePossessionState(
            FriendlyByteBuf buffer) {
        return new PossessionStatePayload(buffer);
    }

    private static void handlePossessionState(PossessionStatePayload payload,
                                              NetworkEvent.Context context) {
        if (context.getSender() == null) {
            MobPossessionClientState.accept(payload);
        }
        context.setPacketHandled(true);
    }

    private static void encodePossessionInput(PossessionInputPayload payload,
                                              FriendlyByteBuf buffer) {
        payload.write(buffer);
    }

    private static PossessionInputPayload decodePossessionInput(
            FriendlyByteBuf buffer) {
        return new PossessionInputPayload(buffer);
    }

    private static void handlePossessionInput(PossessionInputPayload payload,
                                              NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null) {
            MobPossessionManager.acceptInput(player, payload);
        }
        context.setPacketHandled(true);
    }

    private static void encodePossessionAction(PossessionActionPayload payload,
                                               FriendlyByteBuf buffer) {
        payload.write(buffer);
    }

    private static PossessionActionPayload decodePossessionAction(
            FriendlyByteBuf buffer) {
        return new PossessionActionPayload(buffer);
    }

    private static void handlePossessionAction(PossessionActionPayload payload,
                                               NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null) {
            MobPossessionManager.action(player, payload.action());
        }
        context.setPacketHandled(true);
    }
}

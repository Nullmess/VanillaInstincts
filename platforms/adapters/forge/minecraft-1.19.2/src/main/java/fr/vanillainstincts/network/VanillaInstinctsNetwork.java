package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.possession.MobPossessionManager;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Forge 1.19.2 SimpleImpl bridge for the shared Vanilla Instincts payload model. */
public final class VanillaInstinctsNetwork {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            VanillaInstincts.id("main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private VanillaInstinctsNetwork() {
    }

    public static void register(IEventBus ignoredModEventBus) {
        int id = 0;
        CHANNEL.registerMessage(id++, GrandMasterProgressPayload.class,
                VanillaInstinctsNetwork::encodeGrandMaster,
                VanillaInstinctsNetwork::decodeGrandMaster,
                VanillaInstinctsNetwork::handleGrandMaster);
        CHANNEL.registerMessage(id++, PossessionStatePayload.class,
                VanillaInstinctsNetwork::encodePossessionState,
                VanillaInstinctsNetwork::decodePossessionState,
                VanillaInstinctsNetwork::handlePossessionState);
        CHANNEL.registerMessage(id++, PossessionInputPayload.class,
                VanillaInstinctsNetwork::encodePossessionInput,
                VanillaInstinctsNetwork::decodePossessionInput,
                VanillaInstinctsNetwork::handlePossessionInput);
        CHANNEL.registerMessage(id, PossessionActionPayload.class,
                VanillaInstinctsNetwork::encodePossessionAction,
                VanillaInstinctsNetwork::decodePossessionAction,
                VanillaInstinctsNetwork::handlePossessionAction);
    }

    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
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
                                          Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ForgeClientPacketHandler.accept(payload)));
        context.setPacketHandled(true);
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
                                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ForgeClientPacketHandler.accept(payload)));
        context.setPacketHandled(true);
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
                                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                MobPossessionManager.acceptInput(player, payload);
            }
        });
        context.setPacketHandled(true);
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
                                               Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                MobPossessionManager.action(player, payload.action());
            }
        });
        context.setPacketHandled(true);
    }
}

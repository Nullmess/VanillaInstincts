package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.possession.MobPossessionManager;
import java.util.function.Supplier;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

/** Forge 1.19 SimpleImpl bridge for the shared Vanilla Instincts payload model. */
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

    public static void sendToPlayer(ServerPlayerEntity player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    private static void encodeGrandMaster(GrandMasterProgressPayload payload,
                                          PacketBuffer buffer) {
        buffer.writeVarInt(payload.containerId());
        buffer.writeVarInt(payload.masterTrades());
        buffer.writeVarInt(payload.masteredDays());
        buffer.writeBoolean(payload.grandMaster());
    }

    private static GrandMasterProgressPayload decodeGrandMaster(
            PacketBuffer buffer) {
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
                                              PacketBuffer buffer) {
        buffer.writeBoolean(payload.active());
        buffer.writeVarInt(payload.entityId());
    }

    private static PossessionStatePayload decodePossessionState(
            PacketBuffer buffer) {
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
                                              PacketBuffer buffer) {
        buffer.writeVarInt(payload.flags());
        buffer.writeVarInt(payload.yaw100());
        buffer.writeVarInt(payload.pitch100());
    }

    private static PossessionInputPayload decodePossessionInput(
            PacketBuffer buffer) {
        return new PossessionInputPayload(buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt());
    }

    private static void handlePossessionInput(PossessionInputPayload payload,
                                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayerEntity player = context.getSender();
            if (player != null) {
                MobPossessionManager.acceptInput(player, payload);
            }
        });
        context.setPacketHandled(true);
    }

    private static void encodePossessionAction(PossessionActionPayload payload,
                                               PacketBuffer buffer) {
        buffer.writeVarInt(payload.action());
    }

    private static PossessionActionPayload decodePossessionAction(
            PacketBuffer buffer) {
        return new PossessionActionPayload(buffer.readVarInt());
    }

    private static void handlePossessionAction(PossessionActionPayload payload,
                                               Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayerEntity player = context.getSender();
            if (player != null) {
                MobPossessionManager.action(player, payload.action());
            }
        });
        context.setPacketHandled(true);
    }
}

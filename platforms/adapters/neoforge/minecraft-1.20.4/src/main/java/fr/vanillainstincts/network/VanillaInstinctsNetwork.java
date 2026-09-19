package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.client.GrandMasterMerchantClientState;
import fr.vanillainstincts.client.MobPossessionClientState;
import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;

/** Network registration for synchronized gameplay displays and possession. */
public final class VanillaInstinctsNetwork {
    private VanillaInstinctsNetwork() {
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.SERVER.noArg().send(payload);
    }

    public static void sendToPlayer(ServerPlayer player,
                                    CustomPacketPayload payload) {
        PacketDistributor.PLAYER.with(player).send(payload);
    }

    public static void register(RegisterPayloadHandlerEvent event) {
        var registrar = event.registrar(VanillaInstincts.MOD_ID);

        registrar.play(GrandMasterProgressPayload.ID,
                GrandMasterProgressPayload::new,
                handler -> handler.client((payload, context) ->
                        context.workHandler().submitAsync(() ->
                                GrandMasterMerchantClientState.accept(payload))));

        registrar.play(PossessionStatePayload.ID,
                PossessionStatePayload::new,
                handler -> handler.client((payload, context) ->
                        context.workHandler().submitAsync(() ->
                                MobPossessionClientState.accept(payload))));

        registrar.play(PossessionInputPayload.ID,
                PossessionInputPayload::new,
                handler -> handler.server((payload, context) ->
                        context.workHandler().submitAsync(() -> {
                            if (context.packetHandler()
                                    instanceof ServerGamePacketListenerImpl listener) {
                                MobPossessionManager.acceptInput(listener.player,
                                        payload);
                            }
                        })));

        registrar.play(PossessionActionPayload.ID,
                PossessionActionPayload::new,
                handler -> handler.server((payload, context) ->
                        context.workHandler().submitAsync(() -> {
                            if (context.packetHandler()
                                    instanceof ServerGamePacketListenerImpl listener) {
                                MobPossessionManager.action(listener.player,
                                        payload.action());
                            }
                        })));
    }
}

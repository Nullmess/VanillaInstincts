package fr.vanillainstincts.network;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.client.GrandMasterMerchantClientState;
import fr.vanillainstincts.client.MobPossessionClientState;
import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Network registration for synchronized gameplay displays and possession. */
public final class VanillaInstinctsNetwork {
    private VanillaInstinctsNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(GrandMasterProgressPayload.TYPE,
                GrandMasterProgressPayload.STREAM_CODEC,
                (payload, context) ->
                        GrandMasterMerchantClientState.accept(payload));
        registrar.playToClient(PossessionStatePayload.TYPE,
                PossessionStatePayload.STREAM_CODEC,
                (payload, context) -> MobPossessionClientState.accept(payload));
        registrar.playToServer(PossessionInputPayload.TYPE,
                PossessionInputPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        MobPossessionManager.acceptInput(player, payload);
                    }
                });
        registrar.playToServer(PossessionActionPayload.TYPE,
                PossessionActionPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        MobPossessionManager.action(player, payload.action());
                    }
                });
    }
}

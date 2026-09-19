package fr.vanillainstincts.network;

import fr.vanillainstincts.client.GrandMasterMerchantClientState;
import fr.vanillainstincts.client.MobPossessionClientState;

/** Physical-client packet sinks kept out of the common Forge network class. */
final class ForgeClientPacketHandler {
    private ForgeClientPacketHandler() {
    }

    static void accept(GrandMasterProgressPayload payload) {
        GrandMasterMerchantClientState.accept(payload);
    }

    static void accept(PossessionStatePayload payload) {
        MobPossessionClientState.accept(payload);
    }
}

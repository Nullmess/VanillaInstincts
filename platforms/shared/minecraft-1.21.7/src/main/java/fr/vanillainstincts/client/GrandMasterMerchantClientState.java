package fr.vanillainstincts.client;

import fr.vanillainstincts.network.GrandMasterProgressPayload;
import fr.vanillainstincts.village.VillagerGrandMasterController;

/** Client cache scoped to the currently open merchant container. */
public final class GrandMasterMerchantClientState {
    private static volatile Snapshot current;

    private GrandMasterMerchantClientState() {
    }

    public static void accept(GrandMasterProgressPayload payload) {
        if (payload == null) return;
        current = new Snapshot(payload.containerId(),
                Math.min(VillagerGrandMasterController.REQUIRED_MASTER_TRADES,
                        payload.masterTrades()),
                Math.max(0, payload.masteredDays()),
                payload.grandMaster());
    }

    public static Snapshot forContainer(int containerId) {
        Snapshot snapshot = current;
        return snapshot != null && snapshot.containerId() == containerId
                ? snapshot : null;
    }

    public static void clear(int containerId) {
        Snapshot snapshot = current;
        if (snapshot != null && snapshot.containerId() == containerId) {
            current = null;
        }
    }

    public record Snapshot(int containerId, int masterTrades,
                           int masteredDays, boolean grandMaster) {
        public float progress() {
            if (grandMaster) return 1.0F;
            return Math.max(0.0F, Math.min(1.0F, masterTrades
                    / (float) VillagerGrandMasterController
                    .REQUIRED_MASTER_TRADES));
        }
    }
}

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

    public static class Snapshot {
        private final int containerId;
        private final int masterTrades;
        private final int masteredDays;
        private final boolean grandMaster;

        public Snapshot(int containerId, int masterTrades, int masteredDays, boolean grandMaster) {
            this.containerId = containerId;
            this.masterTrades = masterTrades;
            this.masteredDays = masteredDays;
            this.grandMaster = grandMaster;
        }

        public int containerId() { return this.containerId; }

        public int masterTrades() { return this.masterTrades; }

        public int masteredDays() { return this.masteredDays; }

        public boolean grandMaster() { return this.grandMaster; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Snapshot)) return false;
            Snapshot that = (Snapshot) other;
            return this.containerId == that.containerId && this.masterTrades == that.masterTrades && this.masteredDays == that.masteredDays && this.grandMaster == that.grandMaster;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.containerId, this.masterTrades, this.masteredDays, this.grandMaster); }

        @Override
        public String toString() {
            return "Snapshot[" + "containerId=" + this.containerId + ", " + "masterTrades=" + this.masterTrades + ", " + "masteredDays=" + this.masteredDays + ", " + "grandMaster=" + this.grandMaster + "]";
        }

        public float progress() {
            if (grandMaster) return 1.0F;
            return Math.max(0.0F, Math.min(1.0F, masterTrades
                    / (float) VillagerGrandMasterController
                    .REQUIRED_MASTER_TRADES));
        }
    }
}

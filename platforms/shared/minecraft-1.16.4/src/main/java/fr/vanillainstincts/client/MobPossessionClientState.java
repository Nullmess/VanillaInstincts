package fr.vanillainstincts.client;

import fr.vanillainstincts.network.PossessionStatePayload;

/** Client-only possession synchronization state without physical-client links. */
public final class MobPossessionClientState {
    private static volatile boolean active;
    private static volatile int entityId = -1;

    private MobPossessionClientState() {
    }

    public static void accept(PossessionStatePayload payload) {
        if (payload == null) return;
        active = payload.active();
        entityId = active ? payload.entityId() : -1;
    }

    public static boolean active() {
        return active;
    }

    public static int entityId() {
        return entityId;
    }

    public static void clear() {
        active = false;
        entityId = -1;
    }
}

package fr.vanillainstincts.core.policy;

import fr.vanillainstincts.core.model.NetherReinforcementKind;
import fr.vanillainstincts.core.model.NetherReinforcementRole;
import fr.vanillainstincts.core.rules.NetherRules;

/** Pure limits and transitions for cross-dimensional reinforcement missions. */
public final class NetherReinforcementPolicy {
    private NetherReinforcementPolicy() {
    }

    public static boolean shouldUseVirtualExpedition(boolean playerNearPortal) {
        return !playerNearPortal;
    }

    public static int maximumReinforcements(NetherReinforcementKind kind) {
        return kind == NetherReinforcementKind.ZOGLIN
                ? NetherRules.NETHER_ZOGLIN_MAX_MEMBERS
                : NetherRules.NETHER_REINFORCEMENT_MAX_MEMBERS;
    }

    public static int simulatedReinforcementCount(
            NetherReinforcementKind kind, long seed) {
        return simulatedReinforcementCount(kind, seed,
                maximumReinforcements(kind));
    }

    public static int simulatedReinforcementCount(
            NetherReinforcementKind kind, long seed, int configuredMaximum) {
        int vanillaMaximum = maximumReinforcements(kind);
        int maximum = Math.max(0, Math.min(vanillaMaximum,
                configuredMaximum));
        if (maximum == 0) return 0;
        return 1 + (int) Math.floorMod(seed, (long) maximum);
    }

    public static boolean returnsToNetherAfterDefeat(
            NetherReinforcementRole role) {
        return role == NetherReinforcementRole.REINFORCEMENT;
    }

    public static boolean shouldRetreatAfterUnavailable(
            long gameTime, long unavailableAt) {
        return gameTime >= unavailableAt
                + NetherRules.NETHER_PLAYER_UNAVAILABLE_GRACE_TICKS;
    }

    public static boolean portalFailureGraceExpired(
            long gameTime, long failureAt) {
        return failureAt > 0L && gameTime >= failureAt
                + NetherRules.NETHER_PORTAL_FAILURE_GRACE_TICKS;
    }

    public static int portalDetectionRadius(NetherReinforcementKind kind) {
        return kind == NetherReinforcementKind.ZOMBIFIED_PIGLIN
                || kind == NetherReinforcementKind.ZOGLIN
                ? NetherRules.NETHER_MESSENGER_PORTAL_RADIUS : 0;
    }

    public static boolean mayPropagate(int depth) {
        return depth < NetherRules.NETHER_REINFORCEMENT_MAX_PROPAGATION_DEPTH;
    }
}

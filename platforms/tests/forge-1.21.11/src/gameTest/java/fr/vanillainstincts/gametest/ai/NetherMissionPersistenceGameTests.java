package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.NetherMissionSavedData;
import fr.vanillainstincts.ai.NetherReinforcementController;
import fr.vanillainstincts.ai.NetherReinforcementState;
import fr.vanillainstincts.core.model.NetherReinforcementKind;
import fr.vanillainstincts.core.model.NetherReinforcementPhase;
import fr.vanillainstincts.core.rules.NetherRules;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetherMissionPersistenceGameTests {
    private NetherMissionPersistenceGameTests() {
    }

    @GameTest(batch = "nether_mission_persistence", templateNamespace = "vanillainstincts", template = "empty")
    public static void savedVirtualExpeditionRoundTrips(GameTestHelper helper) {
        NetherMissionSavedData data = new NetherMissionSavedData();
        UUID mission = UUID.randomUUID();
        UUID aggressor = UUID.randomUUID();
        UUID messenger = UUID.randomUUID();
        NetherMissionSavedData.VirtualExpeditionRecord record =
                new NetherMissionSavedData.VirtualExpeditionRecord(
                        mission, aggressor,
                        NetherReinforcementKind.ZOMBIFIED_PIGLIN,
                        new BlockPos(2, 1, 3), new BlockPos(4, 2, 5),
                        100L, 900L, 260L, messenger, false, 0L);
        data.putVirtual(record);
        CompoundTag tag = data.saveTag(new CompoundTag());
        NetherMissionSavedData loaded =
                NetherMissionSavedData.load(tag);
        NetherMissionSavedData.VirtualExpeditionRecord restored =
                loaded.virtualExpeditions().iterator().next();
        helper.assertValueEqual(restored.missionId(), mission,
                net.minecraft.network.chat.Component.literal("L'UUID de mission doit survivre au SavedData"));
        helper.assertValueEqual(restored.overworldPortal(),
                new BlockPos(2, 1, 3),
                net.minecraft.network.chat.Component.literal("Le portail Overworld doit survivre au SavedData"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_persistence", templateNamespace = "vanillainstincts", template = "empty")
    public static void savedVirtualCancellationRoundTrips(GameTestHelper helper) {
        NetherMissionSavedData data = new NetherMissionSavedData();
        NetherMissionSavedData.VirtualExpeditionRecord record =
                virtualRecord(true, 0L);
        data.putVirtual(record);
        NetherMissionSavedData loaded = NetherMissionSavedData.load(
                data.saveTag(new CompoundTag()));
        helper.assertTrue(loaded.virtualExpeditions().iterator().next()
                        .cancelReinforcements(),
                net.minecraft.network.chat.Component.literal("L'annulation des renforts doit survivre au redémarrage"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_persistence", templateNamespace = "vanillainstincts", template = "empty")
    public static void portalFailureTimestampRoundTrips(GameTestHelper helper) {
        NetherMissionSavedData data = new NetherMissionSavedData();
        data.putVirtual(virtualRecord(false, 345L));
        NetherMissionSavedData loaded = NetherMissionSavedData.load(
                data.saveTag(new CompoundTag()));
        helper.assertValueEqual(loaded.virtualExpeditions().iterator().next()
                        .portalFailureSince(), 345L,
                net.minecraft.network.chat.Component.literal("Le délai de portail détruit doit survivre au redémarrage"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_persistence", templateNamespace = "vanillainstincts", template = "empty")
    public static void defeatedAggressorRoundTrips(GameTestHelper helper) {
        UUID aggressor = UUID.randomUUID();
        NetherMissionSavedData data = new NetherMissionSavedData();
        data.markDefeated(aggressor, 120L);
        NetherMissionSavedData loaded = NetherMissionSavedData.load(
                data.saveTag(new CompoundTag()));
        helper.assertValueEqual(loaded.defeatedAggressors().get(aggressor),
                120L, net.minecraft.network.chat.Component.literal("La mort ciblée doit survivre au SavedData"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_persistence", templateNamespace = "vanillainstincts", template = "empty")
    public static void unavailableAggressorRoundTrips(GameTestHelper helper) {
        UUID aggressor = UUID.randomUUID();
        NetherMissionSavedData data = new NetherMissionSavedData();
        data.markUnavailable(aggressor, 200L);
        NetherMissionSavedData loaded = NetherMissionSavedData.load(
                data.saveTag(new CompoundTag()));
        helper.assertValueEqual(loaded.unavailableAggressors().get(aggressor),
                200L, net.minecraft.network.chat.Component.literal("La déconnexion doit survivre au SavedData"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_persistence", templateNamespace = "vanillainstincts", template = "empty")
    public static void reconnectClearsUnavailableRecord(GameTestHelper helper) {
        UUID aggressor = UUID.randomUUID();
        NetherMissionSavedData data = new NetherMissionSavedData();
        data.markUnavailable(aggressor, 200L);
        data.clearUnavailable(aggressor);
        helper.assertFalse(data.unavailableAggressors()
                        .containsKey(aggressor),
                net.minecraft.network.chat.Component.literal("La reconnexion doit retirer le délai d'absence"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_persistence", templateNamespace = "vanillainstincts", template = "empty")
    public static void tombstoneExpiresCleanly(GameTestHelper helper) {
        UUID mission = UUID.randomUUID();
        NetherMissionSavedData data = new NetherMissionSavedData();
        data.putVirtualizedUntil(mission, 300L);
        data.cleanup(300L, 24_000L);
        helper.assertFalse(data.virtualizedUntil().containsKey(mission),
                net.minecraft.network.chat.Component.literal("Un tombstone expiré doit être nettoyé"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_persistence", templateNamespace = "vanillainstincts", template = "empty")
    public static void disconnectGraceDoesNotRetreatEarly(GameTestHelper helper) {
        helper.assertFalse(NetherReinforcementController
                        .shouldRetreatAfterUnavailable(699L, 100L),
                net.minecraft.network.chat.Component.literal("Le renfort doit attendre pendant le délai de grâce"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_persistence", templateNamespace = "vanillainstincts", template = "empty")
    public static void disconnectGraceRetreatsAtDeadline(GameTestHelper helper) {
        helper.assertTrue(NetherReinforcementController
                        .shouldRetreatAfterUnavailable(700L, 100L),
                net.minecraft.network.chat.Component.literal("Le renfort doit repartir à la fin du délai de grâce"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_persistence", templateNamespace = "vanillainstincts", template = "empty")
    public static void portalFailureWaitsBeforeFallback(GameTestHelper helper) {
        helper.assertFalse(NetherReinforcementController
                        .portalFailureGraceExpired(699L, 100L),
                net.minecraft.network.chat.Component.literal("Le portail voisin doit pouvoir réapparaître pendant le délai"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_persistence", templateNamespace = "vanillainstincts", template = "empty")
    public static void portalFailureFallsBackAtDeadline(GameTestHelper helper) {
        helper.assertTrue(NetherReinforcementController
                        .portalFailureGraceExpired(700L, 100L),
                net.minecraft.network.chat.Component.literal("La mission doit abandonner proprement le portail détruit"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_persistence", templateNamespace = "vanillainstincts", template = "empty")
    public static void expiredReinforcementCanEnterRetreat(GameTestHelper helper) {
        ZombifiedPiglin piglin = helper.spawn(EntityType.ZOMBIFIED_PIGLIN,
                new BlockPos(2, 1, 2));
        long now = helper.getLevel().getGameTime();
        beginReinforcement(piglin,
                NetherReinforcementKind.ZOMBIFIED_PIGLIN, now);
        NetherReinforcementState state =
                NetherReinforcementState.load(piglin);
        state.beginRetreat(now + 700L,
                NetherRules.NETHER_REINFORCEMENT_RETREAT_TICKS);
        helper.assertValueEqual(state.phase(),
                fr.vanillainstincts.core.model.NetherReinforcementPhase.RETURN_TO_NETHER,
                net.minecraft.network.chat.Component.literal("Un renfort expiré doit pouvoir entrer en retour"));
        helper.succeed();
    }

    private static NetherMissionSavedData.VirtualExpeditionRecord
    virtualRecord(boolean canceled, long portalFailureSince) {
        return new NetherMissionSavedData.VirtualExpeditionRecord(
                UUID.randomUUID(), UUID.randomUUID(),
                NetherReinforcementKind.ZOGLIN,
                new BlockPos(2, 1, 3), new BlockPos(4, 2, 5),
                100L, 900L, 260L, UUID.randomUUID(), canceled,
                portalFailureSince);
    }

    private static void beginReinforcement(
            net.minecraft.world.entity.Mob mob,
            NetherReinforcementKind kind, long now) {
        NetherReinforcementState state = new NetherReinforcementState();
        state.beginReinforcement(UUID.randomUUID(), UUID.randomUUID(), kind,
                new BlockPos(2, 1, 3), new BlockPos(2, 1, 3),
                now, now + 600L, 0);
        state.save(mob);
    }
}

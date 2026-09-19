package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.EnderDragonCrystalGuard;
import fr.vanillainstincts.ai.MissionZoglinTargetPolicy;
import fr.vanillainstincts.ai.NetherReinforcementState;
import fr.vanillainstincts.core.model.NetherReinforcementKind;
import fr.vanillainstincts.core.model.NetherReinforcementPhase;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zoglin;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetherMissionCombatGameTests {
    private NetherMissionCombatGameTests() {
    }

    @GameTest(batch = "nether_mission_combat", templateNamespace = "vanillainstincts", template = "empty")
    public static void missionZoglinIsRecognized(GameTestHelper helper) {
        Zoglin zoglin = helper.spawn(EntityType.ZOGLIN,
                new BlockPos(2, 1, 2));
        NetherReinforcementState state = assaultState(
                helper.getLevel().getGameTime(), UUID.randomUUID());
        helper.assertTrue(MissionZoglinTargetPolicy
                        .isMissionZoglin(zoglin, state),
                net.minecraft.network.chat.Component.literal("Le zoglin recruté par une vache doit être reconnu"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_combat", templateNamespace = "vanillainstincts", template = "empty")
    public static void exactAggressorIsAuthorized(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        UUID aggressor = UUID.randomUUID();
        NetherReinforcementState state = assaultState(now, aggressor);
        helper.assertTrue(MissionZoglinTargetPolicy.canAttackPlayer(
                        state, aggressor, true, false, false, now),
                net.minecraft.network.chat.Component.literal("Seul l'UUID exact du joueur responsable doit être autorisé"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_combat", templateNamespace = "vanillainstincts", template = "empty")
    public static void otherPlayerUuidIsRejected(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        NetherReinforcementState state = assaultState(now,
                UUID.randomUUID());
        helper.assertFalse(MissionZoglinTargetPolicy.canAttackPlayer(
                        state, UUID.randomUUID(), true, false, false, now),
                net.minecraft.network.chat.Component.literal("Un autre joueur ne doit jamais être ciblé"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_combat", templateNamespace = "vanillainstincts", template = "empty")
    public static void deadAggressorIsRejected(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        UUID aggressor = UUID.randomUUID();
        NetherReinforcementState state = assaultState(now, aggressor);
        helper.assertFalse(MissionZoglinTargetPolicy.canAttackPlayer(
                        state, aggressor, false, false, false, now),
                net.minecraft.network.chat.Component.literal("Un joueur mort ne doit plus être une cible valide"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_combat", templateNamespace = "vanillainstincts", template = "empty")
    public static void creativeAggressorIsRejected(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        UUID aggressor = UUID.randomUUID();
        NetherReinforcementState state = assaultState(now, aggressor);
        helper.assertFalse(MissionZoglinTargetPolicy.canAttackPlayer(
                        state, aggressor, true, true, false, now),
                net.minecraft.network.chat.Component.literal("Un joueur créatif ne doit pas être attaqué"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_combat", templateNamespace = "vanillainstincts", template = "empty")
    public static void spectatorAggressorIsRejected(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        UUID aggressor = UUID.randomUUID();
        NetherReinforcementState state = assaultState(now, aggressor);
        helper.assertFalse(MissionZoglinTargetPolicy.canAttackPlayer(
                        state, aggressor, true, false, true, now),
                net.minecraft.network.chat.Component.literal("Un joueur spectateur ne doit pas être attaqué"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_combat", templateNamespace = "vanillainstincts", template = "empty")
    public static void returningZoglinCannotAttack(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        UUID aggressor = UUID.randomUUID();
        NetherReinforcementState state = assaultState(now, aggressor);
        state.beginRetreat(now, 2400L);
        helper.assertFalse(MissionZoglinTargetPolicy.canAttackPlayer(
                        state, aggressor, true, false, false, now),
                net.minecraft.network.chat.Component.literal("Un zoglin en retour doit ignorer toutes les entités"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_combat", templateNamespace = "vanillainstincts", template = "empty")
    public static void nonPlayerEntityIsRejected(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        NetherReinforcementState state = assaultState(now,
                UUID.randomUUID());
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        helper.assertFalse(MissionZoglinTargetPolicy.canAttackTarget(
                        state, cow, now),
                net.minecraft.network.chat.Component.literal("La vache et toutes les autres entités doivent être ignorées"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_combat", templateNamespace = "vanillainstincts", template = "empty")
    public static void ordinaryZoglinAttackIsNotIntercepted(
            GameTestHelper helper) {
        Zoglin zoglin = helper.spawn(EntityType.ZOGLIN,
                new BlockPos(2, 1, 2));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 1, 2));
        helper.assertFalse(MissionZoglinTargetPolicy.shouldBlockAttack(
                        cow, zoglin),
                net.minecraft.network.chat.Component.literal("Un zoglin vanilla sans mission ne doit pas être modifié"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_combat", templateNamespace = "vanillainstincts", template = "empty")
    public static void noCrystalIsDetected(GameTestHelper helper) {
        helper.assertFalse(EnderDragonCrystalGuard.containsActiveCrystal(
                        List.of()),
                net.minecraft.network.chat.Component.literal("Une collection vide ne doit contenir aucun cristal actif"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_combat", templateNamespace = "vanillainstincts", template = "empty")
    public static void activeCrystalIsDetected(GameTestHelper helper) {
        Entity crystal = helper.spawn(EntityType.END_CRYSTAL,
                new BlockPos(2, 2, 2));
        helper.assertTrue(EnderDragonCrystalGuard.containsActiveCrystal(
                        List.of(crystal)),
                net.minecraft.network.chat.Component.literal("Un cristal actif doit rendre la protection disponible"));
        helper.succeed();
    }

    @GameTest(batch = "nether_mission_combat", templateNamespace = "vanillainstincts", template = "empty")
    public static void removedCrystalIsIgnored(GameTestHelper helper) {
        Entity crystal = helper.spawn(EntityType.END_CRYSTAL,
                new BlockPos(2, 2, 2));
        crystal.discard();
        helper.assertFalse(EnderDragonCrystalGuard.containsActiveCrystal(
                        List.of(crystal)),
                net.minecraft.network.chat.Component.literal("Un cristal détruit ou retiré ne doit plus protéger le dragon"));
        helper.succeed();
    }

    private static NetherReinforcementState assaultState(
            long now, UUID aggressor) {
        NetherReinforcementState state = new NetherReinforcementState();
        state.beginReinforcement(UUID.randomUUID(), aggressor,
                NetherReinforcementKind.ZOGLIN,
                new BlockPos(2, 1, 3), new BlockPos(4, 1, 3),
                now, now + 1200L, 0);
        state.setPhase(NetherReinforcementPhase.ASSAULT);
        return state;
    }
}

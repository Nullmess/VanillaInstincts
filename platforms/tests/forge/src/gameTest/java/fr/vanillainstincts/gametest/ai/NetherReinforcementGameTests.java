package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.EndermanTacticsController;
import fr.vanillainstincts.ai.GolemConstructionController;
import fr.vanillainstincts.ai.NetherReinforcementController;
import fr.vanillainstincts.ai.NetherReinforcementState;
import fr.vanillainstincts.core.model.EndermanCargoRole;
import fr.vanillainstincts.core.model.GolemDefenseRole;
import fr.vanillainstincts.core.model.NetherReinforcementKind;
import fr.vanillainstincts.core.model.NetherReinforcementRole;
import fr.vanillainstincts.village.GolemDefenseController;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class NetherReinforcementGameTests {
    private NetherReinforcementGameTests() {
    }

    @GameTest(batch = "nether_reinforcement", template = "empty")
    public static void pigSelectsZombifiedPiglins(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        helper.assertValueEqual(
                NetherReinforcementController.kindForMessenger(pig),
                NetherReinforcementKind.ZOMBIFIED_PIGLIN,
                "Le cochon doit chercher des cochons zombifiés");
        helper.succeed();
    }

    @GameTest(batch = "nether_reinforcement", template = "empty")
    public static void cowSelectsZoglins(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        helper.assertValueEqual(
                NetherReinforcementController.kindForMessenger(cow),
                NetherReinforcementKind.ZOGLIN,
                "La vache doit chercher des zoglins");
        helper.succeed();
    }

    @GameTest(batch = "nether_reinforcement", template = "empty")
    public static void piglinHordeIsLimitedToSix(GameTestHelper helper) {
        helper.assertValueEqual(
                NetherReinforcementController.maximumReinforcements(
                        NetherReinforcementKind.ZOMBIFIED_PIGLIN),
                6, "La horde de cochons zombifiés doit être limitée à six");
        helper.succeed();
    }

    @GameTest(batch = "nether_reinforcement", template = "empty")
    public static void zoglinGroupIsLimitedToTwo(GameTestHelper helper) {
        helper.assertValueEqual(
                NetherReinforcementController.maximumReinforcements(
                        NetherReinforcementKind.ZOGLIN),
                2, "La vache ne doit ramener qu'un ou deux zoglins");
        helper.succeed();
    }

    @GameTest(batch = "nether_reinforcement", template = "empty")
    public static void reinforcementReturnsAfterDefeat(GameTestHelper helper) {
        helper.assertTrue(
                NetherReinforcementController.returnsToNetherAfterDefeat(
                        NetherReinforcementRole.REINFORCEMENT),
                "Les renforts doivent repartir au Nether");
        helper.succeed();
    }

    @GameTest(batch = "nether_reinforcement", template = "empty")
    public static void messengerDoesNotReturnAfterDefeat(GameTestHelper helper) {
        helper.assertFalse(
                NetherReinforcementController.returnsToNetherAfterDefeat(
                        NetherReinforcementRole.MESSENGER),
                "Le cochon ou la vache doit rester dans l'Overworld");
        helper.succeed();
    }

    @GameTest(batch = "nether_reinforcement", template = "empty")
    public static void messengerStateSurvivesReload(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        long now = helper.getLevel().getGameTime();
        UUID aggressor = UUID.randomUUID();
        NetherReinforcementState state = new NetherReinforcementState();
        state.beginMessenger(UUID.randomUUID(), aggressor,
                NetherReinforcementKind.ZOMBIFIED_PIGLIN,
                new BlockPos(2, 1, 3), now, 600L, 200L);
        state.save(pig);
        NetherReinforcementState loaded =
                NetherReinforcementState.load(pig);
        helper.assertValueEqual(loaded.role(),
                NetherReinforcementRole.MESSENGER,
                "Le rôle du messager doit survivre au NBT");
        helper.assertValueEqual(loaded.aggressorId().orElse(null), aggressor,
                "L'UUID de l'agresseur doit survivre au NBT");
        helper.succeed();
    }

    @GameTest(batch = "nether_reinforcement", template = "empty")
    public static void reinforcementStateSurvivesReload(GameTestHelper helper) {
        Zoglin zoglin = helper.spawn(EntityType.ZOGLIN,
                new BlockPos(2, 1, 2));
        long now = helper.getLevel().getGameTime();
        beginReinforcement(zoglin, NetherReinforcementKind.ZOGLIN, now);
        NetherReinforcementState loaded =
                NetherReinforcementState.load(zoglin);
        helper.assertValueEqual(loaded.role(),
                NetherReinforcementRole.REINFORCEMENT,
                "Le rôle de renfort doit survivre au NBT");
        helper.succeed();
    }

    @GameTest(batch = "nether_reinforcement", template = "empty")
    public static void skeletonIsArcherCargo(GameTestHelper helper) {
        Skeleton skeleton = helper.spawn(EntityType.SKELETON,
                new BlockPos(2, 1, 2));
        helper.assertValueEqual(EndermanTacticsController.roleFor(skeleton),
                EndermanCargoRole.ARCHER_PLATFORM,
                "Un squelette doit être reconnu comme archer transportable");
        helper.succeed();
    }

    @GameTest(batch = "nether_reinforcement", template = "empty")
    public static void endermanCannotCarryEnderman(GameTestHelper helper) {
        EnderMan carrier = helper.spawn(EntityType.ENDERMAN,
                new BlockPos(2, 1, 2));
        EnderMan cargo = helper.spawn(EntityType.ENDERMAN,
                new BlockPos(3, 1, 2));
        helper.assertFalse(EndermanTacticsController.canCarry(carrier, cargo),
                "Un Enderman ne doit pas transporter un autre Enderman");
        helper.succeed();
    }

    @GameTest(batch = "nether_reinforcement", template = "empty")
    public static void singleGolemBecomesInterceptor(GameTestHelper helper) {
        helper.assertValueEqual(GolemDefenseController.roleForIndex(0, 1),
                GolemDefenseRole.INTERCEPTOR,
                "Un golem seul doit intercepter la menace");
        helper.succeed();
    }

    @GameTest(batch = "nether_reinforcement", template = "empty")
    public static void fatalFallThresholdIsTwentyFour(GameTestHelper helper) {
        helper.assertValueEqual(
                GolemConstructionController
                        .lethalFallDistanceForHealth(20.0D),
                24.0D,
                "Le seuil minimal de chute mortelle doit rester à 24 blocs");
        helper.succeed();
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

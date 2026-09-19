package fr.vanillainstincts.gametest.world;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.world.NetherEndBedController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BedPart;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DimensionBedGameTests {
    private DimensionBedGameTests() {
    }

    @GameTest(batch = "dimension_beds", templateNamespace = "vanillainstincts", template = "empty")
    public static void netherAllowsRealBedSleep(GameTestHelper helper) {
        helper.assertTrue(NetherEndBedController.isSupportedDimension(Level.NETHER),
                net.minecraft.network.chat.Component.literal("Le Nether doit utiliser le sommeil sans explosion"));
        helper.succeed();
    }

    @GameTest(batch = "dimension_beds", templateNamespace = "vanillainstincts", template = "empty")
    public static void endAllowsRealBedSleep(GameTestHelper helper) {
        helper.assertTrue(NetherEndBedController.isSupportedDimension(Level.END),
                net.minecraft.network.chat.Component.literal("L'End doit utiliser le sommeil sans explosion"));
        helper.succeed();
    }

    @GameTest(batch = "dimension_beds", templateNamespace = "vanillainstincts", template = "empty")
    public static void overworldKeepsVanillaBedBehavior(GameTestHelper helper) {
        helper.assertFalse(NetherEndBedController.isSupportedDimension(Level.OVERWORLD),
                net.minecraft.network.chat.Component.literal("L'Overworld doit rester entièrement vanilla"));
        helper.succeed();
    }

    @GameTest(batch = "dimension_beds", templateNamespace = "vanillainstincts", template = "empty")
    public static void customDimensionIsNotChanged(GameTestHelper helper) {
        ResourceKey<Level> custom = ResourceKey.create(
                Registries.DIMENSION, VanillaInstincts.id("bed_test_dimension"));
        helper.assertFalse(NetherEndBedController.isSupportedDimension(custom),
                net.minecraft.network.chat.Component.literal("Une dimension personnalisée ne doit pas être modifiée"));
        helper.succeed();
    }

    @GameTest(batch = "dimension_beds", templateNamespace = "vanillainstincts", template = "empty")
    public static void netherDimensionRefusalIsCleared(GameTestHelper helper) {
        helper.assertTrue(NetherEndBedController.shouldClearSleepProblem(
                        Level.NETHER, Player.BedSleepingProblem.NOT_POSSIBLE_HERE),
                net.minecraft.network.chat.Component.literal("Le refus dimensionnel du Nether doit être levé"));
        helper.succeed();
    }

    @GameTest(batch = "dimension_beds", templateNamespace = "vanillainstincts", template = "empty")
    public static void endFixedTimeRefusalIsCleared(GameTestHelper helper) {
        helper.assertTrue(NetherEndBedController.shouldClearSleepProblem(
                        Level.END, Player.BedSleepingProblem.NOT_POSSIBLE_NOW),
                net.minecraft.network.chat.Component.literal("Le temps fixe de l'End ne doit pas empêcher l'endormissement"));
        helper.succeed();
    }

    @GameTest(batch = "dimension_beds", templateNamespace = "vanillainstincts", template = "empty")
    public static void fixedTimeDoesNotWakeSleeper(GameTestHelper helper) {
        helper.assertTrue(NetherEndBedController.shouldContinueSleeping(
                        Level.END, Player.BedSleepingProblem.NOT_POSSIBLE_NOW),
                net.minecraft.network.chat.Component.literal("Le temps fixe ne doit pas réveiller immédiatement le joueur"));
        helper.succeed();
    }

    @GameTest(batch = "dimension_beds", templateNamespace = "vanillainstincts", template = "empty")
    public static void missingBedStillWakesSleeper(GameTestHelper helper) {
        helper.assertFalse(NetherEndBedController.shouldContinueSleeping(
                        Level.NETHER, Player.BedSleepingProblem.NOT_POSSIBLE_HERE),
                net.minecraft.network.chat.Component.literal("Un lit retiré doit toujours réveiller le joueur"));
        helper.succeed();
    }

    @GameTest(batch = "dimension_beds", templateNamespace = "vanillainstincts", template = "empty")
    public static void unsafeBedStillRefusesSleep(GameTestHelper helper) {
        helper.assertFalse(NetherEndBedController.shouldClearSleepProblem(
                        Level.NETHER, Player.BedSleepingProblem.NOT_SAFE),
                net.minecraft.network.chat.Component.literal("Les monstres proches doivent rester un refus valide"));
        helper.succeed();
    }

    @GameTest(batch = "dimension_beds", templateNamespace = "vanillainstincts", template = "empty")
    public static void distantBedStillRefusesSleep(GameTestHelper helper) {
        helper.assertFalse(NetherEndBedController.shouldClearSleepProblem(
                        Level.END, Player.BedSleepingProblem.TOO_FAR_AWAY),
                net.minecraft.network.chat.Component.literal("Un lit trop éloigné doit rester refusé"));
        helper.succeed();
    }

    @GameTest(batch = "dimension_beds", templateNamespace = "vanillainstincts", template = "empty")
    public static void clickingBedHeadKeepsPosition(GameTestHelper helper) {
        BlockPos clicked = new BlockPos(2, 1, 2);
        helper.assertValueEqual(NetherEndBedController.resolveHeadPosition(
                        clicked, Direction.NORTH, BedPart.HEAD), clicked,
                net.minecraft.network.chat.Component.literal("Cliquer la tête du lit doit conserver sa position"));
        helper.succeed();
    }

    @GameTest(batch = "dimension_beds", templateNamespace = "vanillainstincts", template = "empty")
    public static void clickingBedFootResolvesHead(GameTestHelper helper) {
        BlockPos clicked = new BlockPos(2, 1, 2);
        helper.assertValueEqual(NetherEndBedController.resolveHeadPosition(
                        clicked, Direction.EAST, BedPart.FOOT), clicked.east(),
                net.minecraft.network.chat.Component.literal("Cliquer le pied doit retrouver la tête du lit"));
        helper.succeed();
    }
}

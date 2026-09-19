package fr.vanillainstincts.gametest.world;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.world.TrappedChestPrankController;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrappedChestPrankGameTests {
    private TrappedChestPrankGameTests() {
    }

    @GameTest(batch = "trapped_chest_prank", templateNamespace = "vanillainstincts", template = "empty")
    public static void trappedChestIsEligible(GameTestHelper helper) {
        helper.assertTrue(TrappedChestPrankController.isEligible(
                        Blocks.TRAPPED_CHEST.defaultBlockState()),
                "Le troll doit être réservé au coffre piégé");
        helper.succeed();
    }

    @GameTest(batch = "trapped_chest_prank", templateNamespace = "vanillainstincts", template = "empty")
    public static void normalChestIsIgnored(GameTestHelper helper) {
        helper.assertFalse(TrappedChestPrankController.isEligible(
                        Blocks.CHEST.defaultBlockState()),
                "Un coffre normal ne doit jamais déclencher ce troll");
        helper.succeed();
    }

    @GameTest(batch = "trapped_chest_prank", templateNamespace = "vanillainstincts", template = "empty")
    public static void triggerChanceStaysRare(GameTestHelper helper) {
        helper.assertTrue(TrappedChestPrankController.TRIGGER_CHANCE > 0.0D
                        && TrappedChestPrankController.TRIGGER_CHANCE <= 0.03D,
                "La fausse alerte doit rester rare");
        helper.succeed();
    }

    @GameTest(batch = "trapped_chest_prank", templateNamespace = "vanillainstincts", template = "empty")
    public static void lowRollTriggersPrank(GameTestHelper helper) {
        helper.assertTrue(TrappedChestPrankController.rollTriggers(0.0D),
                "Un tirage inférieur au seuil doit déclencher le troll");
        helper.succeed();
    }

    @GameTest(batch = "trapped_chest_prank", templateNamespace = "vanillainstincts", template = "empty")
    public static void thresholdRollDoesNotTrigger(GameTestHelper helper) {
        helper.assertFalse(TrappedChestPrankController.rollTriggers(
                        TrappedChestPrankController.TRIGGER_CHANCE),
                "Le seuil lui-même doit rester hors de la probabilité");
        helper.succeed();
    }

    @GameTest(batch = "trapped_chest_prank", templateNamespace = "vanillainstincts", template = "empty")
    public static void delayAlwaysStartsAfterOpening(GameTestHelper helper) {
        helper.assertTrue(TrappedChestPrankController.delayFromUnit(0.0D) > 0,
                "Le son ne doit pas être joué exactement au clic");
        helper.succeed();
    }

    @GameTest(batch = "trapped_chest_prank", templateNamespace = "vanillainstincts", template = "empty")
    public static void delayNeverExceedsConfiguredMaximum(GameTestHelper helper) {
        helper.assertValueEqual(TrappedChestPrankController.delayFromUnit(1.0D),
                TrappedChestPrankController.MAX_DELAY_TICKS,
                "Le délai doit rester court et prévisible");
        helper.succeed();
    }

    @GameTest(batch = "trapped_chest_prank", templateNamespace = "vanillainstincts", template = "empty")
    public static void tntVariantUsesOnlyFuseSound(GameTestHelper helper) {
        TrappedChestPrankController.PrankKind kind =
                TrappedChestPrankController.prankFromUnit(0.1D);
        helper.assertTrue(kind == TrappedChestPrankController.PrankKind.TNT_FUSE
                        && kind.sound() == SoundEvents.TNT_PRIMED,
                "La variante TNT doit uniquement jouer le son d'amorçage");
        helper.succeed();
    }

    @GameTest(batch = "trapped_chest_prank", templateNamespace = "vanillainstincts", template = "empty")
    public static void creeperVariantUsesOnlyHissSound(GameTestHelper helper) {
        TrappedChestPrankController.PrankKind kind =
                TrappedChestPrankController.prankFromUnit(0.9D);
        helper.assertTrue(kind == TrappedChestPrankController.PrankKind.CREEPER_HISS
                        && kind.sound() == SoundEvents.CREEPER_PRIMED,
                "La variante creeper doit uniquement jouer son sifflement");
        helper.succeed();
    }

    @GameTest(batch = "trapped_chest_prank", templateNamespace = "vanillainstincts", template = "empty")
    public static void variantsUseDifferentSoundCategories(GameTestHelper helper) {
        helper.assertTrue(TrappedChestPrankController.PrankKind.TNT_FUSE.source()
                        == SoundSource.BLOCKS
                        && TrappedChestPrankController.PrankKind.CREEPER_HISS.source()
                        == SoundSource.HOSTILE,
                "Les deux trolls doivent conserver leur spatialisation naturelle");
        helper.succeed();
    }

    @GameTest(batch = "trapped_chest_prank", templateNamespace = "vanillainstincts", template = "empty")
    public static void rapidReopenIsThrottled(GameTestHelper helper) {
        long next = 1_200L;
        helper.assertFalse(TrappedChestPrankController.canRoll(1_199L, next),
                "Une réouverture rapide ne doit pas relancer le hasard");
        helper.succeed();
    }

    @GameTest(batch = "trapped_chest_prank", templateNamespace = "vanillainstincts", template = "empty")
    public static void successCooldownIsLongerThanRollCooldown(GameTestHelper helper) {
        helper.assertTrue(TrappedChestPrankController.SUCCESS_COOLDOWN_TICKS
                        > TrappedChestPrankController.ROLL_COOLDOWN_TICKS * 10,
                "Un coffre ayant trollé doit rester silencieux longtemps");
        helper.succeed();
    }
}

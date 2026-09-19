package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.GhastFireballVolleyController;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class GhastTacticsGameTests {
    private GhastTacticsGameTests() {
    }

    @GameTest(batch = "ghast_tactics", template = "empty")
    public static void peacefulGhastsNeverVolley(GameTestHelper helper) {
        helper.assertValueEqual(GhastFireballVolleyController
                .returnerChance(Difficulty.PEACEFUL), 0.0D,
                "Le mode paisible ne doit jamais activer le volley");
        helper.succeed();
    }

    @GameTest(batch = "ghast_tactics", template = "empty")
    public static void easyVolleyIsRare(GameTestHelper helper) {
        helper.assertValueEqual(GhastFireballVolleyController
                .returnerChance(Difficulty.EASY), 0.25D,
                "Seul un quart des ghasts faciles doivent jouer");
        helper.succeed();
    }

    @GameTest(batch = "ghast_tactics", template = "empty")
    public static void normalVolleyIsOccasional(GameTestHelper helper) {
        helper.assertValueEqual(GhastFireballVolleyController
                .returnerChance(Difficulty.NORMAL), 0.55D,
                "La difficulté normale doit surprendre sans être systématique");
        helper.succeed();
    }

    @GameTest(batch = "ghast_tactics", template = "empty")
    public static void hardVolleyIsCommon(GameTestHelper helper) {
        helper.assertValueEqual(GhastFireballVolleyController
                .returnerChance(Difficulty.HARD), 0.85D,
                "La majorité des ghasts difficiles doivent savoir renvoyer");
        helper.succeed();
    }

    @GameTest(batch = "ghast_tactics", template = "empty")
    public static void easyGhastReturnsOnce(GameTestHelper helper) {
        helper.assertValueEqual(GhastFireballVolleyController
                .maxReturns(Difficulty.EASY, 123), 1,
                "Un ghast facile ne doit renvoyer qu'une fois");
        helper.succeed();
    }

    @GameTest(batch = "ghast_tactics", template = "empty")
    public static void normalRallyHasFiniteRange(GameTestHelper helper) {
        int low = GhastFireballVolleyController.maxReturns(Difficulty.NORMAL, 0);
        int high = GhastFireballVolleyController.maxReturns(Difficulty.NORMAL, 2);
        helper.assertTrue(low == 2 && high == 4,
                "Le rallye normal doit durer de deux à quatre renvois");
        helper.succeed();
    }

    @GameTest(batch = "ghast_tactics", template = "empty")
    public static void hardRallyHasFiniteRange(GameTestHelper helper) {
        int low = GhastFireballVolleyController.maxReturns(Difficulty.HARD, 0);
        int high = GhastFireballVolleyController.maxReturns(Difficulty.HARD, 4);
        helper.assertTrue(low == 4 && high == 8,
                "Le rallye difficile doit durer de quatre à huit renvois");
        helper.succeed();
    }

    @GameTest(batch = "ghast_tactics", template = "empty")
    public static void ghastKeepsVanillaFireRate(GameTestHelper helper) {
        helper.assertTrue(!GhastFireballVolleyController
                        .allowsOnlyOneActiveFireball(),
                "VanillaInstincts ne doit jamais limiter les tirs vanilla du ghast");
        helper.succeed();
    }

    @GameTest(batch = "ghast_tactics", template = "empty")
    public static void inboundBallIsRecognized(GameTestHelper helper) {
        helper.assertTrue(GhastFireballVolleyController.isApproaching(
                        new Vec3(1.0D, 0.0D, 0.0D),
                        new Vec3(4.0D, 0.0D, 0.0D), 0.58D),
                "Une boule dirigée vers le ghast doit être reconnue");
        helper.succeed();
    }

    @GameTest(batch = "ghast_tactics", template = "empty")
    public static void outboundBallIsIgnored(GameTestHelper helper) {
        helper.assertTrue(!GhastFireballVolleyController.isApproaching(
                        new Vec3(-1.0D, 0.0D, 0.0D),
                        new Vec3(4.0D, 0.0D, 0.0D), 0.58D),
                "Une boule qui s'éloigne ne doit pas être renvoyée");
        helper.succeed();
    }

    @GameTest(batch = "ghast_tactics", template = "empty")
    public static void rallyAcceleratesWithoutBecomingInfinite(GameTestHelper helper) {
        double first = GhastFireballVolleyController.returnSpeed(1);
        double later = GhastFireballVolleyController.returnSpeed(6);
        double extreme = GhastFireballVolleyController.returnSpeed(100);
        helper.assertTrue(later > first && extreme <= 1.40D,
                "Le duel doit accélérer tout en gardant une vitesse plafonnée");
        helper.succeed();
    }

    @GameTest(batch = "ghast_tactics", template = "empty")
    public static void rallyStopsAtSkillLimit(GameTestHelper helper) {
        helper.assertTrue(GhastFireballVolleyController.canReturn(
                        Difficulty.HARD, true, 3, 4)
                        && !GhastFireballVolleyController.canReturn(
                        Difficulty.HARD, true, 4, 4),
                "Le ghast doit finir par laisser passer la boule");
        helper.succeed();
    }
}

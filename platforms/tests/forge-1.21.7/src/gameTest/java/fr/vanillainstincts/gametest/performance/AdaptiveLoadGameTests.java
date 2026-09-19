package fr.vanillainstincts.gametest.performance;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.core.balance.BehaviorBalancePolicy;
import fr.vanillainstincts.core.config.GameplayProfile;
import fr.vanillainstincts.core.performance.AdaptiveLoadPolicy;
import fr.vanillainstincts.core.performance.AdaptiveLoadState;
import fr.vanillainstincts.core.performance.LoadTier;
import fr.vanillainstincts.core.performance.SchedulerPriority;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdaptiveLoadGameTests {
    private static final long TARGET = 50_000_000L;
    private static final long OVERLOAD = 80_000_000L;

    private AdaptiveLoadGameTests() {
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void healthyLoadRemainsNormal(GameTestHelper helper) {
        helper.assertValueEqual(AdaptiveLoadPolicy.classify(
                        TARGET, 100, 0, TARGET, OVERLOAD),
                LoadTier.NORMAL,
                net.minecraft.network.chat.Component.literal("Une charge saine doit rester au niveau normal"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void slowTickSelectsOverload(GameTestHelper helper) {
        helper.assertValueEqual(AdaptiveLoadPolicy.classify(
                        OVERLOAD, 100, 0, TARGET, OVERLOAD),
                LoadTier.OVERLOADED,
                net.minecraft.network.chat.Component.literal("Le seuil de surcharge doit être immédiat"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void rejectionPressureSelectsStress(GameTestHelper helper) {
        helper.assertValueEqual(AdaptiveLoadPolicy.classify(
                        40_000_000L, 75, 25, TARGET, OVERLOAD),
                LoadTier.STRESSED,
                net.minecraft.network.chat.Component.literal("Les refus de budget doivent participer à la charge"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void degradationIsImmediate(GameTestHelper helper) {
        AdaptiveLoadState state = AdaptiveLoadPolicy.sample(
                AdaptiveLoadState.initial(), 100_000_000L,
                10, 0, TARGET, OVERLOAD, 20);
        helper.assertValueEqual(state.tier(), LoadTier.OVERLOADED,
                net.minecraft.network.chat.Component.literal("La dégradation ne doit pas attendre vingt ticks"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void recoveryUsesHysteresis(GameTestHelper helper) {
        AdaptiveLoadState state = new AdaptiveLoadState(
                40_000_000L, LoadTier.OVERLOADED, 0);
        state = AdaptiveLoadPolicy.sample(state, 40_000_000L,
                10, 0, TARGET, OVERLOAD, 20);
        helper.assertValueEqual(state.tier(), LoadTier.OVERLOADED,
                net.minecraft.network.chat.Component.literal("Une seule bonne mesure ne doit pas réactiver toute l'IA"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void overloadedBudgetIsReduced(GameTestHelper helper) {
        helper.assertValueEqual(AdaptiveLoadPolicy.effectiveCostBudget(
                        320, LoadTier.OVERLOADED, 0.25D), 112,
                net.minecraft.network.chat.Component.literal("Le budget surchargé doit être réduit de manière bornée"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void minimumBudgetPreventsStarvation(GameTestHelper helper) {
        helper.assertValueEqual(AdaptiveLoadPolicy.effectiveCostBudget(
                        320, LoadTier.OVERLOADED, 0.50D), 160,
                net.minecraft.network.chat.Component.literal("Le facteur minimal doit conserver un budget utile"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void backgroundCannotConsumeReserve(GameTestHelper helper) {
        helper.assertFalse(AdaptiveLoadPolicy.reservationAllowed(
                        80, 1, 100, SchedulerPriority.BACKGROUND, 0.20D),
                net.minecraft.network.chat.Component.literal("Le travail lointain ne doit pas voler la réserve visible"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void combatCanUseReserve(GameTestHelper helper) {
        helper.assertTrue(AdaptiveLoadPolicy.reservationAllowed(
                        80, 10, 100, SchedulerPriority.CRITICAL, 0.20D),
                net.minecraft.network.chat.Component.literal("Le combat actif doit rester réactif sous charge"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void overloadedBackgroundCadenceIsSlower(
            GameTestHelper helper) {
        helper.assertValueEqual(AdaptiveLoadPolicy.cadence(
                        4, LoadTier.OVERLOADED,
                        SchedulerPriority.BACKGROUND), 40,
                net.minecraft.network.chat.Component.literal("Les décisions lointaines doivent être espacées"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void visibleCadenceRetainsPriority(GameTestHelper helper) {
        helper.assertValueEqual(AdaptiveLoadPolicy.cadence(
                        4, LoadTier.NORMAL,
                        SchedulerPriority.NEAR_PLAYER), 4,
                net.minecraft.network.chat.Component.literal("La charge normale doit conserver la réactivité visible"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void candidatePrecisionHasAFloor(GameTestHelper helper) {
        helper.assertValueEqual(AdaptiveLoadPolicy.candidateLimit(
                        16, 8, LoadTier.OVERLOADED,
                        SchedulerPriority.BACKGROUND), 8,
                net.minecraft.network.chat.Component.literal("La dégradation ne doit jamais annuler toute recherche"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void heavyTasksAreSpacedUnderLoad(GameTestHelper helper) {
        helper.assertValueEqual(AdaptiveLoadPolicy.heavyTaskGap(
                        50_000_000L, LoadTier.OVERLOADED),
                250_000_000L,
                net.minecraft.network.chat.Component.literal("Les scans lourds doivent ralentir sous surcharge"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void hardProfileDoesNotAccelerateConstruction(
            GameTestHelper helper) {
        helper.assertTrue(GameplayProfile.HARD.constructionMultiplier()
                        <= 1.0D,
                net.minecraft.network.chat.Component.literal("Le profil difficile ne doit pas multiplier le grief"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void performanceProfileHalvesConfiguredBudget(
            GameTestHelper helper) {
        helper.assertValueEqual(GameplayProfile.PERFORMANCE
                        .budgetMultiplier(), 0.50D,
                net.minecraft.network.chat.Component.literal("Le profil performance doit réduire clairement le budget"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void probabilityCanNeverExceedOne(GameTestHelper helper) {
        helper.assertValueEqual(BehaviorBalancePolicy.chance(
                        0.8D, 4.0D, 2.0D), 1.0D,
                net.minecraft.network.chat.Component.literal("Une configuration extrême doit rester probabiliste"));
        helper.succeed();
    }

    @GameTest(batch = "adaptive_load",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void slowTickCadenceMultiplierIsCorrect(
            GameTestHelper helper) {
        helper.assertValueEqual(VanillaInstinctsScheduler
                        .cadenceMultiplier(100_000_000L), 2,
                net.minecraft.network.chat.Component.literal("Cent millisecondes doivent doubler la cadence de fond"));
        helper.succeed();
    }
}

package fr.vanillainstincts.gametest.performance;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.ai.VanillaInstinctsWorkLimiter;
import fr.vanillainstincts.core.policy.VillageGolemPolicy;
import fr.vanillainstincts.core.policy.VillageGrowthPolicy;
import fr.vanillainstincts.village.VillageEvolutionSavedData;
import net.minecraft.core.BlockPos;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SchedulerVillageGrowthGameTests {
    private SchedulerVillageGrowthGameTests() {
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void normalTickKeepsBaseCadence(GameTestHelper helper) {
        helper.assertValueEqual(VanillaInstinctsScheduler
                        .cadenceMultiplier(50_000_000L), 1,
                net.minecraft.network.chat.Component.literal("Le rythme normal doit conserver la cadence de base"));
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void fastTickKeepsBaseCadence(GameTestHelper helper) {
        helper.assertValueEqual(VanillaInstinctsScheduler
                        .cadenceMultiplier(10_000_000L), 1,
                net.minecraft.network.chat.Component.literal("Un tick rapide ne doit pas ralentir artificiellement l'IA"));
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void slowTickReducesAiFrequency(GameTestHelper helper) {
        helper.assertValueEqual(VanillaInstinctsScheduler
                        .cadenceMultiplier(100_000_000L), 2,
                net.minecraft.network.chat.Component.literal("Un tick lent doit espacer les décisions de fond"));
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void firstHeavyTaskIsAllowed(GameTestHelper helper) {
        helper.assertTrue(VanillaInstinctsWorkLimiter.ready(Long.MIN_VALUE, 100L,
                        0L, 1L, 400L),
                net.minecraft.network.chat.Component.literal("La première tâche lourde doit pouvoir démarrer"));
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void realTimeGateBlocksBurst(GameTestHelper helper) {
        helper.assertFalse(VanillaInstinctsWorkLimiter.ready(100L, 1_000L,
                        5_000L, 4_999L, 400L),
                net.minecraft.network.chat.Component.literal("Le temps réel doit bloquer une rafale accélérée"));
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void smallVillageNeedsNoGolem(GameTestHelper helper) {
        helper.assertValueEqual(VillageGolemPolicy.desiredCount(4), 0,
                net.minecraft.network.chat.Component.literal("Quatre adultes ne doivent pas déclencher de golem"));
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void normalVillageNeedsOneGolem(GameTestHelper helper) {
        helper.assertValueEqual(VillageGolemPolicy.desiredCount(12), 1,
                net.minecraft.network.chat.Component.literal("Un village normal doit viser un seul golem"));
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void largeVillageCapsAtTwoGolems(GameTestHelper helper) {
        helper.assertValueEqual(VillageGolemPolicy.desiredCount(200), 2,
                net.minecraft.network.chat.Component.literal("Un grand village doit rester limité à deux golems"));
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void cooldownBlocksReplacement(GameTestHelper helper) {
        helper.assertFalse(VillageGolemPolicy.canBuild(30, 0, 0,
                        13L, 10L),
                net.minecraft.network.chat.Component.literal("Le remplacement doit respecter quatre jours de délai"));
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void housingReserveTriggersGrowth(GameTestHelper helper) {
        helper.assertTrue(VillageGrowthPolicy.needsHousing(6, 7),
                net.minecraft.network.chat.Component.literal("La réserve de logements doit déclencher une nouvelle maison"));
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void growthCellIsStable(GameTestHelper helper) {
        long first = VillageEvolutionSavedData.growthKey(
                new BlockPos(1, 64, 1));
        long second = VillageEvolutionSavedData.growthKey(
                new BlockPos(63, 70, 63));
        helper.assertValueEqual(first, second,
                net.minecraft.network.chat.Component.literal("Un même secteur doit partager sa cadence de croissance"));
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void steepSiteIsRejected(GameTestHelper helper) {
        helper.assertFalse(VillageGrowthPolicy.slopeAccepted(64, 68),
                net.minecraft.network.chat.Component.literal("Une pente forte ne doit pas recevoir une maison"));
        helper.succeed();
    }
}

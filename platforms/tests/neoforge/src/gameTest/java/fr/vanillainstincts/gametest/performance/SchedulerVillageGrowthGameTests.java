package fr.vanillainstincts.gametest.performance;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.ai.VanillaInstinctsWorkLimiter;
import fr.vanillainstincts.core.policy.VillageGolemPolicy;
import fr.vanillainstincts.core.policy.VillageGrowthPolicy;
import fr.vanillainstincts.village.VillageEvolutionSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SchedulerVillageGrowthGameTests {
    private SchedulerVillageGrowthGameTests() {
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void normalTickKeepsBaseCadence(GameTestHelper helper) {
        helper.assertValueEqual(VanillaInstinctsScheduler
                        .cadenceMultiplier(50_000_000L), 1,
                "Le rythme normal doit conserver la cadence de base");
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void fastTickKeepsBaseCadence(GameTestHelper helper) {
        helper.assertValueEqual(VanillaInstinctsScheduler
                        .cadenceMultiplier(10_000_000L), 1,
                "Un tick rapide ne doit pas ralentir artificiellement l'IA");
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void slowTickReducesAiFrequency(GameTestHelper helper) {
        helper.assertValueEqual(VanillaInstinctsScheduler
                        .cadenceMultiplier(100_000_000L), 2,
                "Un tick lent doit espacer les décisions de fond");
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void firstHeavyTaskIsAllowed(GameTestHelper helper) {
        helper.assertTrue(VanillaInstinctsWorkLimiter.ready(Long.MIN_VALUE, 100L,
                        0L, 1L, 400L),
                "La première tâche lourde doit pouvoir démarrer");
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void realTimeGateBlocksBurst(GameTestHelper helper) {
        helper.assertFalse(VanillaInstinctsWorkLimiter.ready(100L, 1_000L,
                        5_000L, 4_999L, 400L),
                "Le temps réel doit bloquer une rafale accélérée");
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void smallVillageNeedsNoGolem(GameTestHelper helper) {
        helper.assertValueEqual(VillageGolemPolicy.desiredCount(4), 0,
                "Quatre adultes ne doivent pas déclencher de golem");
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void normalVillageNeedsOneGolem(GameTestHelper helper) {
        helper.assertValueEqual(VillageGolemPolicy.desiredCount(12), 1,
                "Un village normal doit viser un seul golem");
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void largeVillageCapsAtTwoGolems(GameTestHelper helper) {
        helper.assertValueEqual(VillageGolemPolicy.desiredCount(200), 2,
                "Un grand village doit rester limité à deux golems");
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void cooldownBlocksReplacement(GameTestHelper helper) {
        helper.assertFalse(VillageGolemPolicy.canBuild(30, 0, 0,
                        13L, 10L),
                "Le remplacement doit respecter quatre jours de délai");
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void housingReserveTriggersGrowth(GameTestHelper helper) {
        helper.assertTrue(VillageGrowthPolicy.needsHousing(6, 7),
                "La réserve de logements doit déclencher une nouvelle maison");
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void growthCellIsStable(GameTestHelper helper) {
        long first = VillageEvolutionSavedData.growthKey(
                new BlockPos(1, 64, 1));
        long second = VillageEvolutionSavedData.growthKey(
                new BlockPos(63, 70, 63));
        helper.assertValueEqual(first, second,
                "Un même secteur doit partager sa cadence de croissance");
        helper.succeed();
    }

    @GameTest(batch = "scheduler_village_growth", templateNamespace = "vanillainstincts", template = "empty")
    public static void steepSiteIsRejected(GameTestHelper helper) {
        helper.assertFalse(VillageGrowthPolicy.slopeAccepted(64, 68),
                "Une pente forte ne doit pas recevoir une maison");
        helper.succeed();
    }
}

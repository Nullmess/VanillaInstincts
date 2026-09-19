package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import fr.vanillainstincts.village.ClericBrewingController;
import fr.vanillainstincts.village.VillageGolemCeremonyController;
import fr.vanillainstincts.village.VillageGolemCeremonySavedData;
import fr.vanillainstincts.village.VillagerRoutineController;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class VillageConstructionRulesGameTests {
    private VillageConstructionRulesGameTests() {
    }

    @GameTest(batch = "village_construction_rules", template = "empty")
    public static void livingGolemCountIsCapped(GameTestHelper helper) {
        helper.assertFalse(VillageGolemCeremonyController.mayBuildForCount(50),
                "Les golems vivants doivent bloquer la surproduction");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_rules", template = "empty")
    public static void oneDailyBuildIsAllowed(GameTestHelper helper) {
        helper.assertTrue(VillageGolemCeremonyController.mayBuildToday(0),
                "Le premier golem du jour doit être possible");
        helper.assertFalse(VillageGolemCeremonyController.mayBuildToday(1),
                "Le second golem du jour doit être refusé");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_rules", template = "empty")
    public static void villageCapIsTwoGolems(GameTestHelper helper) {
        helper.assertValueEqual(VillageGolemCeremonyController
                        .desiredGolems(200), 2,
                "Le plafond du village doit rester à deux golems");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_rules", template = "empty")
    public static void firstBuildStartsInMorning(GameTestHelper helper) {
        helper.assertTrue(VillageGolemCeremonyController
                        .isCeremonyWindow(VillageConstructionRules.GOLEM_CEREMONY_FIRST_START, 0),
                "La première équipe doit pouvoir travailler le matin");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_rules", template = "empty")
    public static void secondBuildHasNoWindow(GameTestHelper helper) {
        helper.assertFalse(VillageGolemCeremonyController
                        .isCeremonyWindow(5_000L, 1),
                "Le second golem quotidien ne doit avoir aucune plage");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_rules", template = "empty")
    public static void cooldownBlocksEarlyReplacement(GameTestHelper helper) {
        helper.assertFalse(VillageGolemCeremonyController.mayBuildForVillage(
                        30, 0, 0, 12L, 10L),
                "Le délai inter-journées doit bloquer un remplacement précoce");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_rules", template = "empty")
    public static void savedCounterStopsAtOne(GameTestHelper helper) {
        VillageGolemCeremonySavedData data = new VillageGolemCeremonySavedData();
        long key = 18_018L;
        long day = 18L;
        data.markBuilt(key, day);
        data.markBuilt(key, day);
        helper.assertValueEqual(data.builtToday(key, day), 1,
                "Le compteur persistant doit rester limité à un golem");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_rules", template = "empty")
    public static void workerHasShortCheckInWindow(GameTestHelper helper) {
        helper.assertTrue(VillagerRoutineController
                        .isJobCheckInWindow(3_600L, 0),
                "Le villageois doit parfois revenir réellement à son poste");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_rules", template = "empty")
    public static void workerIsNotAlwaysRecalled(GameTestHelper helper) {
        helper.assertFalse(VillagerRoutineController
                        .isJobCheckInWindow(4_000L, 0),
                "Le villageois ne doit pas être collé constamment à son poste");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_rules", template = "empty")
    public static void workerTooFarIsRecalled(GameTestHelper helper) {
        double far = VillageSocialRules.VILLAGER_JOB_MAX_ROAM_DISTANCE + 1.0D;
        helper.assertTrue(VillagerRoutineController
                        .shouldReturnToJob(4_000L, 0, far * far),
                "Un villageois ne doit pas abandonner complètement son village");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_rules", template = "empty")
    public static void clericLoadsThreeBottlesMaximum(GameTestHelper helper) {
        helper.assertValueEqual(ClericBrewingController.chooseBatchSize(12), 3,
                "Un lot visible doit utiliser les trois emplacements de l'alambic");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_rules", template = "empty")
    public static void blazePowderCanProduceStrength(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.isBrewingIngredient(
                        new ItemStack(Items.BLAZE_POWDER)),
                "La poudre de Blaze doit aussi être reconnue comme ingrédient de force");
        helper.assertTrue(ClericBrewingController
                        .shouldInspectBatch(500L, 420L),
                "Le clerc doit revenir après le temps de brassage");
        helper.succeed();
    }
}

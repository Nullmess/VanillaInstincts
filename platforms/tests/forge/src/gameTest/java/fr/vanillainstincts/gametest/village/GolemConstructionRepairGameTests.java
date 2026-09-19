package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.village.GolemRepairController;
import fr.vanillainstincts.village.VillageGolemCeremonyController;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class GolemConstructionRepairGameTests {
    private GolemConstructionRepairGameTests() {
    }

    @GameTest(batch = "golem_construction_repair", template = "empty")
    public static void smithStartsBeforeFarmerArrives(GameTestHelper helper) {
        helper.assertTrue(VillageGolemCeremonyController
                        .smithCanAdvance(true, 0),
                "Le forgeron prêt doit pouvoir poser le premier bloc seul");
        helper.succeed();
    }

    @GameTest(batch = "golem_construction_repair", template = "empty")
    public static void absentSmithCannotAdvance(GameTestHelper helper) {
        helper.assertFalse(VillageGolemCeremonyController
                        .smithCanAdvance(false, 0),
                "Un forgeron encore en chemin ne doit pas poser à distance");
        helper.succeed();
    }

    @GameTest(batch = "golem_construction_repair", template = "empty")
    public static void smithStopsAfterFourBlocks(GameTestHelper helper) {
        helper.assertFalse(VillageGolemCeremonyController
                        .smithCanAdvance(true, 4),
                "Le forgeron doit s'arrêter après les quatre blocs de fer");
        helper.succeed();
    }

    @GameTest(batch = "golem_construction_repair", template = "empty")
    public static void farmerFinishesCompleteIronShape(GameTestHelper helper) {
        helper.assertTrue(VillageGolemCeremonyController
                        .farmerCanFinish(true, 4),
                "Le fermier prêt doit poser la citrouille après le quatrième bloc");
        helper.succeed();
    }

    @GameTest(batch = "golem_construction_repair", template = "empty")
    public static void farmerWaitsForFourthBlock(GameTestHelper helper) {
        helper.assertFalse(VillageGolemCeremonyController
                        .farmerCanFinish(true, 3),
                "Le fermier ne doit pas poser la citrouille trop tôt");
        helper.succeed();
    }

    @GameTest(batch = "golem_construction_repair", template = "empty")
    public static void oneIngotRepairsTwentyFiveHealth(GameTestHelper helper) {
        helper.assertValueEqual(GolemRepairController.healthAfterIngots(
                        50.0F, 100.0F, 1), 75.0F,
                "Un lingot doit restaurer vingt-cinq points de vie");
        helper.succeed();
    }

    @GameTest(batch = "golem_construction_repair", template = "empty")
    public static void repairNeverExceedsMaximumHealth(GameTestHelper helper) {
        helper.assertValueEqual(GolemRepairController.healthAfterIngots(
                        90.0F, 100.0F, 4), 100.0F,
                "La réparation doit s'arrêter à la vie maximale");
        helper.succeed();
    }

    @GameTest(batch = "golem_construction_repair", template = "empty")
    public static void halfHealthNeedsTwoIngots(GameTestHelper helper) {
        helper.assertValueEqual(GolemRepairController
                        .requiredIngotsForFullRepair(50.0F, 100.0F), 2,
                "Un golem à la moitié de sa vie doit demander deux lingots");
        helper.succeed();
    }

    @GameTest(batch = "golem_construction_repair", template = "empty")
    public static void almostDeadGolemNeedsFourIngots(GameTestHelper helper) {
        helper.assertValueEqual(GolemRepairController
                        .requiredIngotsForFullRepair(1.0F, 100.0F), 4,
                "Un golem presque détruit doit demander quatre lingots");
        helper.succeed();
    }

    @GameTest(batch = "golem_construction_repair", template = "empty")
    public static void enoughIronStartsFullRepair(GameTestHelper helper) {
        helper.assertTrue(GolemRepairController.canFullyRepair(
                        1.0F, 100.0F, 4),
                "Quatre lingots doivent permettre une réparation complète");
        helper.succeed();
    }

    @GameTest(batch = "golem_construction_repair", template = "empty")
    public static void insufficientIronDoesNotStartPartialRepair(GameTestHelper helper) {
        helper.assertFalse(GolemRepairController.canFullyRepair(
                        1.0F, 100.0F, 3),
                "La séquence ne doit pas démarrer sans assez de fer pour finir");
        helper.succeed();
    }

    @GameTest(batch = "golem_construction_repair", template = "empty")
    public static void smithInventoryCountsAllIngots(GameTestHelper helper) {
        SimpleContainer inventory = new SimpleContainer(8);
        inventory.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        inventory.setItem(4, new ItemStack(Items.IRON_INGOT, 3));
        helper.assertValueEqual(GolemRepairController.countIronIngots(
                        inventory), 5,
                "Tous les lingots répartis dans l'inventaire doivent compter");
        helper.succeed();
    }
}

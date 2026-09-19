package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.village.VillageConstructionCapability;
import fr.vanillainstincts.village.VillageGolemCeremonyController;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class VillageConstructionCapabilityGameTests {
    private VillageConstructionCapabilityGameTests() {
    }

    @GameTest(batch = "village_construction_capability", template = "empty")
    public static void masonCanBuildGolemFrame(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability
                        .isBuilderProfession(VillagerProfession.MASON),
                "Le maçon doit pouvoir monter la structure du golem");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_capability", template = "empty")
    public static void armorerCanBuildGolemFrame(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability
                        .isBuilderProfession(VillagerProfession.ARMORER),
                "L'armurier doit rester un constructeur valide");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_capability", template = "empty")
    public static void toolsmithCanBuildGolemFrame(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability
                        .isBuilderProfession(VillagerProfession.TOOLSMITH),
                "Le forgeron d'outils doit rester un constructeur valide");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_capability", template = "empty")
    public static void weaponsmithCanBuildGolemFrame(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability
                        .isBuilderProfession(VillagerProfession.WEAPONSMITH),
                "Le forgeron d'armes doit rester un constructeur valide");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_capability", template = "empty")
    public static void farmerFinishesButDoesNotBuildFrame(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability
                        .isFinisherProfession(VillagerProfession.FARMER),
                "Le fermier doit poser la citrouille finale");
        helper.assertFalse(VillageConstructionCapability
                        .isBuilderProfession(VillagerProfession.FARMER),
                "Le fermier ne doit pas remplacer le constructeur");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_capability", template = "empty")
    public static void masonCanInitiateCeremony(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability
                        .canInitiate(VillagerProfession.MASON),
                "Un maçon doit pouvoir chercher un fermier");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_capability", template = "empty")
    public static void farmerCanInitiateCeremony(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability
                        .canInitiate(VillagerProfession.FARMER),
                "Un fermier doit pouvoir chercher un constructeur");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_capability", template = "empty")
    public static void genericBuilderMayPlaceIron(GameTestHelper helper) {
        helper.assertTrue(VillageGolemCeremonyController
                        .builderCanAdvance(true, 2),
                "Le placement ne doit plus dépendre du nom forgeron");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_capability", template = "empty")
    public static void thirdCeremonyWaitsUntilLateDay(GameTestHelper helper) {
        helper.assertFalse(VillageGolemCeremonyController
                        .isCeremonyWindow(8_000L, 2),
                "La troisième construction doit attendre sa plage de fin de journée");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_capability", template = "empty")
    public static void secondCeremonyIsBlockedAfterDailyLimit(
            GameTestHelper helper) {
        helper.assertFalse(VillageGolemCeremonyController
                        .isCeremonyWindow(8_000L, 1),
                "Une seule cérémonie doit être autorisée par jour");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_capability", template = "empty")
    public static void exactBellPositionsHaveDifferentVillageKeys(GameTestHelper helper) {
        long first = VillageGolemCeremonyController.villageKey(
                new BlockPos(1, 64, 1));
        long second = VillageGolemCeremonyController.villageKey(
                new BlockPos(2, 64, 1));
        helper.assertTrue(first != second,
                "Deux cloches proches ne doivent plus partager une clé grossière");
        helper.succeed();
    }

    @GameTest(batch = "village_construction_capability", template = "empty")
    public static void mixedIronStockCountsExactly(GameTestHelper helper) {
        SimpleContainer inventory = new SimpleContainer(8);
        inventory.setItem(0, new ItemStack(Items.IRON_BLOCK, 2));
        inventory.setItem(3, new ItemStack(Items.IRON_INGOT, 18));
        helper.assertValueEqual(VillageGolemCeremonyController
                        .ironUnits(inventory), 36,
                "Les blocs et lingots doivent former un stock commun exact");
        helper.succeed();
    }
}

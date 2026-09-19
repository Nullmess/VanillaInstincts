package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.village.CartographerExpeditionController;
import fr.vanillainstincts.village.FishermanController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import fr.vanillainstincts.village.VillagerProductionController;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProfessionExpeditionGameTests {
    private ProfessionExpeditionGameTests() {
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void treasureRateIsRare(GameTestHelper helper) {
        helper.assertTrue(CartographerExpeditionController
                .shouldFindTreasure(8L),
                "La huitième expédition peut chercher un trésor");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void treasureRateRejectsCommonTrip(GameTestHelper helper) {
        helper.assertFalse(CartographerExpeditionController
                .shouldFindTreasure(7L),
                "Une expédition ordinaire ne doit pas chercher un trésor");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void expeditionRadiusHasLowerBound(GameTestHelper helper) {
        int radius = CartographerExpeditionController.expeditionRadius(0L);
        helper.assertTrue(radius
                >= ProfessionRules.CARTOGRAPHER_EXPLORE_RADIUS_MIN,
                "Le cartographe doit quitter le poste");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void expeditionRadiusHasUpperBound(GameTestHelper helper) {
        for (long sequence = 0L; sequence < 128L; sequence++) {
            int radius = CartographerExpeditionController
                    .expeditionRadius(sequence);
            helper.assertTrue(radius
                    <= ProfessionRules.CARTOGRAPHER_EXPLORE_RADIUS_MAX,
                    "Le rayon doit rester borné");
        }
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void armorerShowsIron(GameTestHelper helper) {
        helper.assertTrue(VillagerProductionController
                .workItem(VillagerProfession.ARMORER).is(Items.IRON_INGOT),
                "L'armurier doit montrer son métal");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void butcherShowsMeat(GameTestHelper helper) {
        helper.assertTrue(VillagerProductionController
                .workItem(VillagerProfession.BUTCHER).is(Items.BEEF),
                "Le boucher doit montrer sa matière");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void cartographerShowsCraftingMaterial(GameTestHelper helper) {
        helper.assertTrue(VillagerProductionController
                .workItem(VillagerProfession.CARTOGRAPHER)
                .is(Items.SUGAR_CANE),
                "Le cartographe doit montrer un ingrédient de travail");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void librarianShowsCraftingMaterial(GameTestHelper helper) {
        helper.assertTrue(VillagerProductionController
                .workItem(VillagerProfession.LIBRARIAN)
                .is(Items.SUGAR_CANE),
                "Le bibliothécaire doit montrer un ingrédient de travail");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void genericProfessionsHaveVisualStock(
            GameTestHelper helper) {
        List<VillagerProfession> professions = List.of(
                VillagerProfession.ARMORER,
                VillagerProfession.BUTCHER,
                VillagerProfession.CARTOGRAPHER,
                VillagerProfession.FLETCHER,
                VillagerProfession.LEATHERWORKER,
                VillagerProfession.LIBRARIAN,
                VillagerProfession.MASON,
                VillagerProfession.SHEPHERD,
                VillagerProfession.TOOLSMITH,
                VillagerProfession.WEAPONSMITH);
        for (VillagerProfession profession : professions) {
            helper.assertFalse(VillagerProductionController
                    .workItem(profession).isEmpty(),
                    "Chaque métier générique doit montrer une matière");
        }
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void cartographerTradesMaps(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController.professionWants(
                VillagerProfession.CARTOGRAPHER,
                new ItemStack(Items.FILLED_MAP)),
                "Une carte doit pouvoir circuler dans le village");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void cartographerCatalogContainsMap(GameTestHelper helper) {
        boolean found = VillagerProductionController
                .catalog(VillagerProfession.CARTOGRAPHER)
                .stream().anyMatch(stack -> stack.is(Items.MAP));
        helper.assertTrue(found,
                "Le catalogue du cartographe doit contenir une carte");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void mapTradeStockIsBounded(GameTestHelper helper) {
        helper.assertTrue(ProfessionRules.CARTOGRAPHER_MAP_TRADE_USES > 0
                        && ProfessionRules.CARTOGRAPHER_MAP_TRADE_USES <= 8,
                "Le stock de cartes doit rester borné");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void singleMapCanBeExchanged(GameTestHelper helper) {
        int amount = VillagerFoodExchangeController.exchangeAmountForTest(
                VillagerProfession.CARTOGRAPHER,
                new ItemStack(Items.FILLED_MAP));
        helper.assertValueEqual(amount, 1,
                "Une carte unique doit pouvoir être remise");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void allProducedGoodsCanBeExchanged(GameTestHelper helper) {
        List<VillagerProfession> professions = List.of(
                VillagerProfession.ARMORER,
                VillagerProfession.BUTCHER,
                VillagerProfession.CARTOGRAPHER,
                VillagerProfession.FLETCHER,
                VillagerProfession.LEATHERWORKER,
                VillagerProfession.LIBRARIAN,
                VillagerProfession.MASON,
                VillagerProfession.SHEPHERD,
                VillagerProfession.TOOLSMITH,
                VillagerProfession.WEAPONSMITH);
        for (VillagerProfession profession : professions) {
            for (ItemStack product : VillagerProductionController
                    .catalog(profession)) {
                int amount = VillagerFoodExchangeController
                        .exchangeAmountForTest(profession, product.copy());
                helper.assertTrue(amount > 0,
                        "Chaque produit doit pouvoir circuler");
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void farmerCanExchangeFood(GameTestHelper helper) {
        int amount = VillagerFoodExchangeController.exchangeAmountForTest(
                VillagerProfession.FARMER,
                new ItemStack(Items.BREAD, 16));
        helper.assertTrue(amount > 0,
                "Le fermier doit pouvoir remettre sa nourriture");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void fishermanCanExchangeCatch(GameTestHelper helper) {
        int amount = VillagerFoodExchangeController.exchangeAmountForTest(
                VillagerProfession.FISHERMAN,
                new ItemStack(Items.COD));
        helper.assertValueEqual(amount, 1,
                "Le pêcheur doit pouvoir remettre sa prise");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void clericCanExchangePotion(GameTestHelper helper) {
        int amount = VillagerFoodExchangeController.exchangeAmountForTest(
                VillagerProfession.CLERIC,
                new ItemStack(Items.POTION));
        helper.assertValueEqual(amount, 1,
                "Le clerc doit pouvoir remettre une potion");
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void waterScanIsBounded(GameTestHelper helper) {
        helper.assertTrue(FishermanController.waterScanChecks() <= 2_048,
                "La recherche d'eau doit rester bornée");
        helper.succeed();
    }
}

package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.village.VillagerProfessionCompat;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.village.CartographerExpeditionController;
import fr.vanillainstincts.village.FishermanController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import fr.vanillainstincts.village.VillagerProductionController;
import java.util.List;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

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
                net.minecraft.network.chat.Component.literal("La huitième expédition peut chercher un trésor"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void treasureRateRejectsCommonTrip(GameTestHelper helper) {
        helper.assertFalse(CartographerExpeditionController
                .shouldFindTreasure(7L),
                net.minecraft.network.chat.Component.literal("Une expédition ordinaire ne doit pas chercher un trésor"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void expeditionRadiusHasLowerBound(GameTestHelper helper) {
        int radius = CartographerExpeditionController.expeditionRadius(0L);
        helper.assertTrue(radius
                >= ProfessionRules.CARTOGRAPHER_EXPLORE_RADIUS_MIN,
                net.minecraft.network.chat.Component.literal("Le cartographe doit quitter le poste"));
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
                    net.minecraft.network.chat.Component.literal("Le rayon doit rester borné"));
        }
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void armorerShowsIron(GameTestHelper helper) {
        helper.assertTrue(VillagerProductionController
                .workItem(VillagerProfessionCompat.value(VillagerProfession.ARMORER)).is(Items.IRON_INGOT),
                net.minecraft.network.chat.Component.literal("L'armurier doit montrer son métal"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void butcherShowsMeat(GameTestHelper helper) {
        helper.assertTrue(VillagerProductionController
                .workItem(VillagerProfessionCompat.value(VillagerProfession.BUTCHER)).is(Items.BEEF),
                net.minecraft.network.chat.Component.literal("Le boucher doit montrer sa matière"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void cartographerShowsCraftingMaterial(GameTestHelper helper) {
        helper.assertTrue(VillagerProductionController
                .workItem(VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER))
                .is(Items.SUGAR_CANE),
                net.minecraft.network.chat.Component.literal("Le cartographe doit montrer un ingrédient de travail"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void librarianShowsCraftingMaterial(GameTestHelper helper) {
        helper.assertTrue(VillagerProductionController
                .workItem(VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN))
                .is(Items.SUGAR_CANE),
                net.minecraft.network.chat.Component.literal("Le bibliothécaire doit montrer un ingrédient de travail"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void genericProfessionsHaveVisualStock(
            GameTestHelper helper) {
        List<VillagerProfession> professions = List.of(
                VillagerProfessionCompat.value(VillagerProfession.ARMORER),
                VillagerProfessionCompat.value(VillagerProfession.BUTCHER),
                VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER),
                VillagerProfessionCompat.value(VillagerProfession.FLETCHER),
                VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER),
                VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN),
                VillagerProfessionCompat.value(VillagerProfession.MASON),
                VillagerProfessionCompat.value(VillagerProfession.SHEPHERD),
                VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH),
                VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH));
        for (VillagerProfession profession : professions) {
            helper.assertFalse(VillagerProductionController
                    .workItem(profession).isEmpty(),
                    net.minecraft.network.chat.Component.literal("Chaque métier générique doit montrer une matière"));
        }
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void cartographerTradesMaps(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController.professionWants(
                VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER),
                new ItemStack(Items.FILLED_MAP)),
                net.minecraft.network.chat.Component.literal("Une carte doit pouvoir circuler dans le village"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void cartographerCatalogContainsMap(GameTestHelper helper) {
        boolean found = VillagerProductionController
                .catalog(VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER))
                .stream().anyMatch(stack -> stack.is(Items.MAP));
        helper.assertTrue(found,
                net.minecraft.network.chat.Component.literal("Le catalogue du cartographe doit contenir une carte"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void mapTradeStockIsBounded(GameTestHelper helper) {
        helper.assertTrue(ProfessionRules.CARTOGRAPHER_MAP_TRADE_USES > 0
                        && ProfessionRules.CARTOGRAPHER_MAP_TRADE_USES <= 8,
                net.minecraft.network.chat.Component.literal("Le stock de cartes doit rester borné"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void singleMapCanBeExchanged(GameTestHelper helper) {
        int amount = VillagerFoodExchangeController.exchangeAmountForTest(
                VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER),
                new ItemStack(Items.FILLED_MAP));
        helper.assertValueEqual(amount, 1,
                net.minecraft.network.chat.Component.literal("Une carte unique doit pouvoir être remise"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void allProducedGoodsCanBeExchanged(GameTestHelper helper) {
        List<VillagerProfession> professions = List.of(
                VillagerProfessionCompat.value(VillagerProfession.ARMORER),
                VillagerProfessionCompat.value(VillagerProfession.BUTCHER),
                VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER),
                VillagerProfessionCompat.value(VillagerProfession.FLETCHER),
                VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER),
                VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN),
                VillagerProfessionCompat.value(VillagerProfession.MASON),
                VillagerProfessionCompat.value(VillagerProfession.SHEPHERD),
                VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH),
                VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH));
        for (VillagerProfession profession : professions) {
            for (ItemStack product : VillagerProductionController
                    .catalog(profession)) {
                int amount = VillagerFoodExchangeController
                        .exchangeAmountForTest(profession, product.copy());
                helper.assertTrue(amount > 0,
                        net.minecraft.network.chat.Component.literal("Chaque produit doit pouvoir circuler"));
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void farmerCanExchangeFood(GameTestHelper helper) {
        int amount = VillagerFoodExchangeController.exchangeAmountForTest(
                VillagerProfessionCompat.value(VillagerProfession.FARMER),
                new ItemStack(Items.BREAD, 16));
        helper.assertTrue(amount > 0,
                net.minecraft.network.chat.Component.literal("Le fermier doit pouvoir remettre sa nourriture"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void fishermanCanExchangeCatch(GameTestHelper helper) {
        int amount = VillagerFoodExchangeController.exchangeAmountForTest(
                VillagerProfessionCompat.value(VillagerProfession.FISHERMAN),
                new ItemStack(Items.COD));
        helper.assertValueEqual(amount, 1,
                net.minecraft.network.chat.Component.literal("Le pêcheur doit pouvoir remettre sa prise"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void clericCanExchangePotion(GameTestHelper helper) {
        int amount = VillagerFoodExchangeController.exchangeAmountForTest(
                VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                new ItemStack(Items.POTION));
        helper.assertValueEqual(amount, 1,
                net.minecraft.network.chat.Component.literal("Le clerc doit pouvoir remettre une potion"));
        helper.succeed();
    }

    @GameTest(batch = "profession_expedition", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void waterScanIsBounded(GameTestHelper helper) {
        helper.assertTrue(FishermanController.waterScanChecks() <= 2_048,
                net.minecraft.network.chat.Component.literal("La recherche d'eau doit rester bornée"));
        helper.succeed();
    }
}

package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.village.ClericBrewingController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class ClericServiceDemandGameTests {
    private ClericServiceDemandGameTests() {
    }

    @GameTest(batch = "cleric_service_demand", template = "empty")
    public static void serviceCatalogContainsRecipes(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.serviceRecipeCount(
                        helper.getLevel()) > 20,
                "Le clerc doit connaître toutes les recettes reconnues par l'alambic");
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", template = "empty")
    public static void serviceCatalogContainsNormalPotions(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.serviceCatalogContainsContainer(
                        helper.getLevel(), Items.POTION),
                "Le catalogue doit contenir les potions normales");
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", template = "empty")
    public static void serviceCatalogContainsSplashPotions(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.serviceCatalogContainsContainer(
                        helper.getLevel(), Items.SPLASH_POTION),
                "Le catalogue doit contenir les potions jetables");
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", template = "empty")
    public static void serviceCatalogContainsLingeringPotions(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.serviceCatalogContainsContainer(
                        helper.getLevel(), Items.LINGERING_POTION),
                "Le catalogue doit contenir les potions persistantes");
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", template = "empty")
    public static void clericMayBrewBeforeDailyLimit(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.mayStartDailyBatch(
                        ProfessionRules.CLERIC_BREW_DAILY_BATCH_LIMIT - 1),
                "Le clerc doit pouvoir produire avant sa limite quotidienne");
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", template = "empty")
    public static void clericStopsAtDailyLimit(GameTestHelper helper) {
        helper.assertFalse(ClericBrewingController.mayStartDailyBatch(
                        ProfessionRules.CLERIC_BREW_DAILY_BATCH_LIMIT),
                "Le clerc doit prendre une longue pause après sa limite quotidienne");
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", template = "empty")
    public static void brewingPauseHasMinimumDuration(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.cooldownAfterBatch(0L)
                        >= ProfessionRules.CLERIC_BREW_COOLDOWN_TICKS,
                "Chaque lot doit être suivi d'une vraie pause");
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", template = "empty")
    public static void brewingPauseIsBounded(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.cooldownAfterBatch(99L)
                        <= ProfessionRules.CLERIC_BREW_COOLDOWN_TICKS
                        + ProfessionRules.CLERIC_BREW_COOLDOWN_VARIATION_TICKS,
                "La pause ne doit pas bloquer définitivement le clerc");
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", template = "empty")
    public static void buyerPaysBeforeDelivery(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController.buyerPaysFirst(),
                "Le client doit lancer les émeraudes avant la marchandise");
        helper.assertTrue(VillagerFoodExchangeController
                        .shouldSeekPurchase(1),
                "Un villageois aléatoire doit pouvoir devenir acheteur");
        helper.assertFalse(VillagerFoodExchangeController
                        .shouldSeekPurchase(3),
                "Tous les villageois ne doivent pas choisir le même rôle");
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", template = "empty")
    public static void clericWantsBrewingMaterials(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController.professionWants(
                        VillagerProfession.CLERIC,
                        new ItemStack(Items.NETHER_WART)),
                "Le clerc doit demander des matières liées à l'alambic");
        helper.assertTrue(VillagerFoodExchangeController.exchangeAmountForTest(
                        VillagerProfession.CLERIC,
                        new ItemStack(Items.POTION)) == 1,
                "Une bouteille réservée doit pouvoir être échangée socialement");
        helper.assertValueEqual(VillagerFoodExchangeController
                        .professionalSaleAmount(VillagerProfession.CLERIC,
                                new ItemStack(Items.POTION)), 1,
                "Un clerc doit vendre une potion pour une émeraude sociale");
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", template = "empty")
    public static void everyProfessionMayWantFood(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController.professionWants(
                        VillagerProfession.ARMORER,
                        new ItemStack(Items.BREAD)),
                "Toutes les professions doivent pouvoir acheter de la nourriture");
        helper.assertValueEqual(VillagerFoodExchangeController
                        .professionalSaleAmount(VillagerProfession.FARMER,
                                new ItemStack(Items.BREAD, 16)), 3,
                "Le fermier doit rendre une quantité cohérente pour une émeraude");
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", template = "empty")
    public static void professionDemandRejectsUnrelatedGoods(GameTestHelper helper) {
        helper.assertFalse(VillagerFoodExchangeController.professionWants(
                        VillagerProfession.LIBRARIAN,
                        new ItemStack(Items.IRON_INGOT)),
                "Un bibliothécaire ne doit pas acheter du fer sans raison");
        helper.succeed();
    }
}

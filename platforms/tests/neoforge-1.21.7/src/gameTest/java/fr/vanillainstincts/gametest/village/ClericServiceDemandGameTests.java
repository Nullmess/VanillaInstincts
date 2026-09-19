package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.village.VillagerProfessionCompat;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.village.ClericBrewingController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClericServiceDemandGameTests {
    private ClericServiceDemandGameTests() {
    }

    @GameTest(batch = "cleric_service_demand", templateNamespace = "vanillainstincts", template = "empty")
    public static void serviceCatalogContainsRecipes(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.serviceRecipeCount(
                        helper.getLevel()) > 20,
                net.minecraft.network.chat.Component.literal("Le clerc doit connaître toutes les recettes reconnues par l'alambic"));
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", templateNamespace = "vanillainstincts", template = "empty")
    public static void serviceCatalogContainsNormalPotions(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.serviceCatalogContainsContainer(
                        helper.getLevel(), Items.POTION),
                net.minecraft.network.chat.Component.literal("Le catalogue doit contenir les potions normales"));
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", templateNamespace = "vanillainstincts", template = "empty")
    public static void serviceCatalogContainsSplashPotions(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.serviceCatalogContainsContainer(
                        helper.getLevel(), Items.SPLASH_POTION),
                net.minecraft.network.chat.Component.literal("Le catalogue doit contenir les potions jetables"));
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", templateNamespace = "vanillainstincts", template = "empty")
    public static void serviceCatalogContainsLingeringPotions(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.serviceCatalogContainsContainer(
                        helper.getLevel(), Items.LINGERING_POTION),
                net.minecraft.network.chat.Component.literal("Le catalogue doit contenir les potions persistantes"));
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", templateNamespace = "vanillainstincts", template = "empty")
    public static void clericMayBrewBeforeDailyLimit(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.mayStartDailyBatch(
                        ProfessionRules.CLERIC_BREW_DAILY_BATCH_LIMIT - 1),
                net.minecraft.network.chat.Component.literal("Le clerc doit pouvoir produire avant sa limite quotidienne"));
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", templateNamespace = "vanillainstincts", template = "empty")
    public static void clericStopsAtDailyLimit(GameTestHelper helper) {
        helper.assertFalse(ClericBrewingController.mayStartDailyBatch(
                        ProfessionRules.CLERIC_BREW_DAILY_BATCH_LIMIT),
                net.minecraft.network.chat.Component.literal("Le clerc doit prendre une longue pause après sa limite quotidienne"));
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", templateNamespace = "vanillainstincts", template = "empty")
    public static void brewingPauseHasMinimumDuration(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.cooldownAfterBatch(0L)
                        >= ProfessionRules.CLERIC_BREW_COOLDOWN_TICKS,
                net.minecraft.network.chat.Component.literal("Chaque lot doit être suivi d'une vraie pause"));
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", templateNamespace = "vanillainstincts", template = "empty")
    public static void brewingPauseIsBounded(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.cooldownAfterBatch(99L)
                        <= ProfessionRules.CLERIC_BREW_COOLDOWN_TICKS
                        + ProfessionRules.CLERIC_BREW_COOLDOWN_VARIATION_TICKS,
                net.minecraft.network.chat.Component.literal("La pause ne doit pas bloquer définitivement le clerc"));
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", templateNamespace = "vanillainstincts", template = "empty")
    public static void buyerPaysBeforeDelivery(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController.buyerPaysFirst(),
                net.minecraft.network.chat.Component.literal("Le client doit lancer les émeraudes avant la marchandise"));
        helper.assertTrue(VillagerFoodExchangeController
                        .shouldSeekPurchase(1),
                net.minecraft.network.chat.Component.literal("Un villageois aléatoire doit pouvoir devenir acheteur"));
        helper.assertFalse(VillagerFoodExchangeController
                        .shouldSeekPurchase(3),
                net.minecraft.network.chat.Component.literal("Tous les villageois ne doivent pas choisir le même rôle"));
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", templateNamespace = "vanillainstincts", template = "empty")
    public static void clericWantsBrewingMaterials(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController.professionWants(
                        VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                        new ItemStack(Items.NETHER_WART)),
                net.minecraft.network.chat.Component.literal("Le clerc doit demander des matières liées à l'alambic"));
        helper.assertTrue(VillagerFoodExchangeController.exchangeAmountForTest(
                        VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                        new ItemStack(Items.POTION)) == 1,
                net.minecraft.network.chat.Component.literal("Une bouteille réservée doit pouvoir être échangée socialement"));
        helper.assertValueEqual(VillagerFoodExchangeController
                        .professionalSaleAmount(VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                                new ItemStack(Items.POTION)), 1,
                net.minecraft.network.chat.Component.literal("Un clerc doit vendre une potion pour une émeraude sociale"));
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", templateNamespace = "vanillainstincts", template = "empty")
    public static void everyProfessionMayWantFood(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController.professionWants(
                        VillagerProfessionCompat.value(VillagerProfession.ARMORER),
                        new ItemStack(Items.BREAD)),
                net.minecraft.network.chat.Component.literal("Toutes les professions doivent pouvoir acheter de la nourriture"));
        helper.assertValueEqual(VillagerFoodExchangeController
                        .professionalSaleAmount(VillagerProfessionCompat.value(VillagerProfession.FARMER),
                                new ItemStack(Items.BREAD, 16)), 3,
                net.minecraft.network.chat.Component.literal("Le fermier doit rendre une quantité cohérente pour une émeraude"));
        helper.succeed();
    }

    @GameTest(batch = "cleric_service_demand", templateNamespace = "vanillainstincts", template = "empty")
    public static void professionDemandRejectsUnrelatedGoods(GameTestHelper helper) {
        helper.assertFalse(VillagerFoodExchangeController.professionWants(
                        VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN),
                        new ItemStack(Items.IRON_INGOT)),
                net.minecraft.network.chat.Component.literal("Un bibliothécaire ne doit pas acheter du fer sans raison"));
        helper.succeed();
    }
}

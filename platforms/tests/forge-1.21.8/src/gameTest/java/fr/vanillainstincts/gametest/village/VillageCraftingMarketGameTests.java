package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.village.VillagerProfessionCompat;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.economy.MarketBalancePolicy;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.village.ProfessionIngredient;
import fr.vanillainstincts.village.ProfessionRecipe;
import fr.vanillainstincts.village.ProfessionRecipeCatalog;
import fr.vanillainstincts.village.ProfessionStockController;
import fr.vanillainstincts.village.VillageMarketSavedData;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import fr.vanillainstincts.village.VillagerProductionController;
import fr.vanillainstincts.village.VillagerRoutineController;
import fr.vanillainstincts.village.VillagerWalletController;
import java.util.List;
import net.minecraft.core.BlockPos;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageCraftingMarketGameTests {
    private VillageCraftingMarketGameTests() {
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void everyRecipeConsumesMaterials(GameTestHelper helper) {
        List<VillagerProfession> professions = craftingProfessions();
        helper.assertTrue(professions.stream().allMatch(profession ->
                        ProfessionRecipeCatalog.recipes(profession).stream()
                                .allMatch(recipe -> !recipe.ingredients().isEmpty()
                                        && !recipe.result().isEmpty())),
                net.minecraft.network.chat.Component.literal("Chaque production doit posséder des entrées et une sortie"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void missingInputBlocksCraft(GameTestHelper helper) {
        ProfessionRecipe recipe = recipe("toolsmith/iron_pickaxe");
        SimpleContainer stock = new SimpleContainer(8);
        stock.addItem(new ItemStack(Items.IRON_INGOT, 3));
        helper.assertFalse(ProfessionRecipeCatalog.canCraft(stock, recipe, 1),
                net.minecraft.network.chat.Component.literal("Les bâtons manquants doivent bloquer la fabrication"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void failedCraftIsAtomic(GameTestHelper helper) {
        ProfessionRecipe recipe = recipe("toolsmith/iron_pickaxe");
        SimpleContainer stock = new SimpleContainer(8);
        stock.addItem(new ItemStack(Items.IRON_INGOT, 3));
        helper.assertFalse(ProfessionRecipeCatalog.consume(stock, recipe, 1),
                net.minecraft.network.chat.Component.literal("Une recette incomplète ne doit pas commencer"));
        helper.assertValueEqual(
                ProfessionRecipeCatalog.countItem(stock, Items.IRON_INGOT), 3,
                net.minecraft.network.chat.Component.literal("Aucun matériau ne doit disparaître après un échec"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void successfulCraftConsumesExactInputs(
            GameTestHelper helper) {
        ProfessionRecipe recipe = recipe("toolsmith/iron_pickaxe");
        SimpleContainer stock = new SimpleContainer(8);
        stock.addItem(new ItemStack(Items.IRON_INGOT, 5));
        stock.addItem(new ItemStack(Items.STICK, 4));
        helper.assertTrue(ProfessionRecipeCatalog.consume(stock, recipe, 1),
                net.minecraft.network.chat.Component.literal("Le forgeron doit pouvoir consommer une recette complète"));
        helper.assertValueEqual(
                ProfessionRecipeCatalog.countItem(stock, Items.IRON_INGOT), 2,
                net.minecraft.network.chat.Component.literal("Trois lingots doivent être consommés"));
        helper.assertValueEqual(
                ProfessionRecipeCatalog.countItem(stock, Items.STICK), 2,
                net.minecraft.network.chat.Component.literal("Deux bâtons doivent être consommés"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void rareRecipesRequireProfessionLevel(
            GameTestHelper helper) {
        ProfessionRecipe recipe = recipe("armorer/diamond_chestplate");
        SimpleContainer stock = new SimpleContainer(8);
        stock.addItem(new ItemStack(Items.DIAMOND, 8));
        helper.assertFalse(ProfessionRecipeCatalog.canCraft(stock, recipe, 4),
                net.minecraft.network.chat.Component.literal("Un expert ne doit pas produire la recette de maître"));
        helper.assertTrue(ProfessionRecipeCatalog.canCraft(stock, recipe, 5),
                net.minecraft.network.chat.Component.literal("Un maître doit pouvoir produire la recette rare"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void interruptedBatchCanRefundInputs(
            GameTestHelper helper) {
        ProfessionRecipe recipe = recipe("fletcher/arrows");
        SimpleContainer stock = new SimpleContainer(8);
        stock.addItem(new ItemStack(Items.FLINT));
        stock.addItem(new ItemStack(Items.STICK));
        stock.addItem(new ItemStack(Items.FEATHER));
        helper.assertTrue(ProfessionRecipeCatalog.consume(stock, recipe, 1),
                net.minecraft.network.chat.Component.literal("Le lot doit commencer"));
        ProfessionRecipeCatalog.refund(stock, recipe);
        for (ProfessionIngredient ingredient : recipe.ingredients()) {
            helper.assertValueEqual(ProfessionRecipeCatalog.countItem(stock,
                            ingredient.item()), ingredient.count(),
                    net.minecraft.network.chat.Component.literal("Une annulation doit rendre chaque matériau"));
        }
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void recipeInputsCreateProfessionDemand(
            GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController.professionWants(
                        VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH),
                        new ItemStack(Items.TRIPWIRE_HOOK)),
                net.minecraft.network.chat.Component.literal("Le fabricant d'arbalètes doit demander ses composants"));
        helper.assertTrue(ProfessionRecipeCatalog.byId(
                        "leatherworker/lead") != null,
                net.minecraft.network.chat.Component.literal("Le travailleur du cuir doit pouvoir fabriquer une laisse"));
        helper.assertTrue(VillagerFoodExchangeController.professionWants(
                        VillagerProfessionCompat.value(VillagerProfession.FARMER),
                        new ItemStack(Items.LEAD)),
                net.minecraft.network.chat.Component.literal("Le fermier doit demander une laisse pour trier les troupeaux"));
        helper.assertValueEqual(ProfessionStockController.targetReserve(
                        VillagerProfessionCompat.value(VillagerProfession.FARMER), new ItemStack(Items.LEAD)), 1,
                net.minecraft.network.chat.Component.literal("La laisse du fermier doit rester un outil réutilisable"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void oneBatchCreatesOneMerchantUse(
            GameTestHelper helper) {
        helper.assertValueEqual(VillagerProductionController.productionUses(
                        new ItemStack(Items.IRON_CHESTPLATE), 10L), 1,
                net.minecraft.network.chat.Component.literal("Un objet produit ne doit pas multiplier le stock marchand"));
        Villager seller = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        seller.setVillagerData(seller.getVillagerData()
                .withProfession(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(VillagerProfession.FARMER).orElseThrow()));
        seller.getInventory().addItem(new ItemStack(Items.BREAD, 16));
        MerchantOffer breadOffer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1),
                new ItemStack(Items.BREAD, 3), 12, 1, 0.05F);
        seller.getOffers().add(breadOffer);
        helper.assertValueEqual(VillagerFoodExchangeController
                        .findProfessionalSaleSlot(seller), -1,
                net.minecraft.network.chat.Component.literal("Un produit déjà proposé au joueur ne doit pas être revendu socialement"));
        breadOffer.setToOutOfStock();
        helper.assertValueEqual(VillagerFoodExchangeController
                        .findProfessionalSaleSlot(seller), -1,
                net.minecraft.network.chat.Component.literal("Une offre épuisée mais encore listée doit rester réservée au commerce joueur"));
        int normal = VillagerProductionController.productionCooldown(seller,
                10L);
        long gameTime = helper.getLevel().getGameTime();
        VillagerProductionController.recordSocialSale(seller,
                helper.getLevel(), new ItemStack(Items.BREAD, 3), gameTime);
        helper.assertTrue(VillagerProductionController.socialBoostActive(
                        seller, gameTime),
                net.minecraft.network.chat.Component.literal("Une vente professionnelle doit activer le bonus de production"));
        helper.assertTrue(VillagerProductionController.productionCooldown(
                        seller, 10L) < normal,
                net.minecraft.network.chat.Component.literal("Le bonus doit accélérer le prochain cycle sans dupliquer le lot"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void armorerKeepsVariedCatalog(GameTestHelper helper) {
        helper.assertTrue(VillagerProductionController.catalog(
                        VillagerProfessionCompat.value(VillagerProfession.ARMORER)).size() >= 10,
                net.minecraft.network.chat.Component.literal("L'économie réelle doit conserver un catalogue varié"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void marketDemandRaisesPrice(GameTestHelper helper) {
        helper.assertTrue(MarketBalancePolicy.quotedPrice(10, 1, 24, 1, 64)
                        > MarketBalancePolicy.quotedPrice(10, 24, 1, 1, 64),
                net.minecraft.network.chat.Component.literal("La demande locale doit augmenter le prix relatif"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void marketRoundTrips(GameTestHelper helper) {
        VillageMarketSavedData source = new VillageMarketSavedData();
        BlockPos pos = new BlockPos(32, 64, 32);
        source.recordSupply(pos, "minecraft:bread", 12, 4L);
        source.recordDemand(pos, "minecraft:bread", 7, 4L);
        CompoundTag tag = source.saveTag(new CompoundTag(),
                helper.getLevel().registryAccess());
        VillageMarketSavedData loaded = VillageMarketSavedData.load(tag,
                helper.getLevel().registryAccess());
        VillageMarketSavedData.MarketEntry value = loaded.snapshot(pos,
                "minecraft:bread", 4L);
        helper.assertValueEqual(value.supply(), 12,
                net.minecraft.network.chat.Component.literal("L'offre doit survivre au redémarrage"));
        helper.assertValueEqual(value.demand(), 7,
                net.minecraft.network.chat.Component.literal("La demande doit survivre au redémarrage"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void oldMarketPressureDecays(GameTestHelper helper) {
        VillageMarketSavedData source = new VillageMarketSavedData();
        BlockPos pos = new BlockPos(32, 64, 32);
        source.recordDemand(pos, "minecraft:bread", 100, 1L);
        helper.assertTrue(source.snapshot(pos, "minecraft:bread", 5L)
                        .demand() < 100,
                net.minecraft.network.chat.Component.literal("Une ancienne pénurie ne doit pas figer les prix"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void walletCannotBecomeNegative(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        VillagerWalletController.setBalanceForTest(villager, 3);
        helper.assertFalse(VillagerWalletController.debit(villager, 4),
                net.minecraft.network.chat.Component.literal("Un villageois ne doit pas créer un paiement à découvert"));
        helper.assertValueEqual(VillagerWalletController.balance(villager), 3,
                net.minecraft.network.chat.Component.literal("Le paiement refusé doit conserver le solde"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void walletTransferConservesEmeralds(GameTestHelper helper) {
        Villager payer = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        Villager seller = helper.spawn(EntityType.VILLAGER,
                new BlockPos(4, 1, 2));
        VillagerWalletController.setBalanceForTest(payer, 20);
        VillagerWalletController.setBalanceForTest(seller, 5);
        helper.assertTrue(VillagerWalletController.transfer(payer, seller, 7),
                net.minecraft.network.chat.Component.literal("Le paiement solvable doit réussir"));
        helper.assertValueEqual(VillagerWalletController.balance(payer), 13,
                net.minecraft.network.chat.Component.literal("Le payeur doit perdre sept émeraudes"));
        helper.assertValueEqual(VillagerWalletController.balance(seller), 12,
                net.minecraft.network.chat.Component.literal("Le vendeur doit gagner sept émeraudes"));
        helper.succeed();
    }

    @GameTest(batch = "village_crafting_market",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void workAndTradeUseHalfDays(GameTestHelper helper) {
        helper.assertTrue(VillagerRoutineController.phaseFor(3_000L)
                        == VillagerSchedulePhase.WORK,
                net.minecraft.network.chat.Component.literal("La première moitié active doit rester productive"));
        helper.assertTrue(VillagerRoutineController.phaseFor(9_000L)
                        == VillagerSchedulePhase.SOCIAL,
                net.minecraft.network.chat.Component.literal("La seconde moitié active doit rester commerciale"));
        helper.succeed();
    }

    private static ProfessionRecipe recipe(String id) {
        ProfessionRecipe recipe = ProfessionRecipeCatalog.byId(id);
        if (recipe == null) throw new IllegalStateException(id);
        return recipe;
    }

    private static List<VillagerProfession> craftingProfessions() {
        return List.of(VillagerProfessionCompat.value(VillagerProfession.ARMORER),
                VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH),
                VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH),
                VillagerProfessionCompat.value(VillagerProfession.BUTCHER),
                VillagerProfessionCompat.value(VillagerProfession.FLETCHER),
                VillagerProfessionCompat.value(VillagerProfession.SHEPHERD),
                VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN),
                VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER),
                VillagerProfessionCompat.value(VillagerProfession.MASON),
                VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER));
    }
}

package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.village.VillagerProfessionCompat;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.village.ClericBrewingController;
import fr.vanillainstincts.village.FarmerController;
import fr.vanillainstincts.village.ProfessionStockController;
import fr.vanillainstincts.village.RecoveredTradeController;
import fr.vanillainstincts.village.SmithRepairController;
import java.util.UUID;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProfessionStockGameTests {
    private ProfessionStockGameTests() {
    }

    @GameTest(batch = "profession_stock", templateNamespace = "vanillainstincts", template = "empty")
    public static void netherWartIsBrewingIngredient(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.isBrewingIngredient(
                        new ItemStack(Items.NETHER_WART)),
                net.minecraft.network.chat.Component.literal("Le clerc doit reconnaître la verrue du Nether"));
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", templateNamespace = "vanillainstincts", template = "empty")
    public static void wheatIsNotBrewingIngredient(GameTestHelper helper) {
        helper.assertFalse(ClericBrewingController.isBrewingIngredient(
                        new ItemStack(Items.WHEAT)),
                net.minecraft.network.chat.Component.literal("Le blé ne doit pas être placé dans l'alambic"));
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", templateNamespace = "vanillainstincts", template = "empty")
    public static void blazePowderIsBrewingFuel(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.isBrewingFuel(
                        new ItemStack(Items.BLAZE_POWDER)),
                net.minecraft.network.chat.Component.literal("La poudre de Blaze doit alimenter l'alambic"));
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", templateNamespace = "vanillainstincts", template = "empty")
    public static void potionIsBrewingContainer(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.isPotionContainer(
                        new ItemStack(Items.POTION)),
                net.minecraft.network.chat.Component.literal("Une potion doit occuper un emplacement de bouteille"));
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", templateNamespace = "vanillainstincts", template = "empty")
    public static void clericRecoversBrewingIngredient(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                        new ItemStack(Items.SUGAR)),
                net.minecraft.network.chat.Component.literal("Le clerc doit récupérer les ingrédients de potion"));
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", templateNamespace = "vanillainstincts", template = "empty")
    public static void smithRecoversIronBlocks(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfessionCompat.value(VillagerProfession.ARMORER),
                        new ItemStack(Items.IRON_BLOCK)),
                net.minecraft.network.chat.Component.literal("Le forgeron doit pouvoir stocker le fer de construction"));
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerKeepsSeedReserve(GameTestHelper helper) {
        helper.assertValueEqual(ProfessionStockController.targetReserve(
                        VillagerProfessionCompat.value(VillagerProfession.FARMER),
                        new ItemStack(Items.WHEAT_SEEDS)), 24,
                net.minecraft.network.chat.Component.literal("Le fermier doit constituer une réserve de graines"));
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", templateNamespace = "vanillainstincts", template = "empty")
    public static void ironSwordUsesIronRepair(GameTestHelper helper) {
        helper.assertTrue(SmithRepairController.repairMaterial(
                        new ItemStack(Items.IRON_SWORD)) == Items.IRON_INGOT,
                net.minecraft.network.chat.Component.literal("Une épée en fer doit consommer du fer pour être réparée"));
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", templateNamespace = "vanillainstincts", template = "empty")
    public static void bowUsesStringRepair(GameTestHelper helper) {
        helper.assertTrue(SmithRepairController.repairMaterial(
                        new ItemStack(Items.BOW)) == Items.STRING,
                net.minecraft.network.chat.Component.literal("Un arc doit consommer de la ficelle pour être réparé"));
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", templateNamespace = "vanillainstincts", template = "empty")
    public static void repairAmountIsBounded(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(180);
        int amount = SmithRepairController.repairAmount(
                new UUID(13L, 13L), sword, 7L);
        helper.assertTrue(amount > 0 && amount <= 180,
                net.minecraft.network.chat.Component.literal("La réparation doit restaurer une partie bornée des dégâts"));
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", templateNamespace = "vanillainstincts", template = "empty")
    public static void repairedEquipmentCostsMore(GameTestHelper helper) {
        UUID merchant = new UUID(130L, 131L);
        ItemStack damaged = new ItemStack(Items.IRON_SWORD);
        damaged.setDamageValue(damaged.getMaxDamage() - 10);
        ItemStack repaired = damaged.copy();
        repaired.setDamageValue(0);
        int low = RecoveredTradeController.emeraldPrice(merchant, damaged,
                2L, 0);
        int high = RecoveredTradeController.emeraldPrice(merchant, repaired,
                2L, 2);
        helper.assertTrue(high > low,
                net.minecraft.network.chat.Component.literal("Le meilleur état et le travail doivent augmenter le prix"));
        helper.assertTrue(RecoveredTradeController.deterministicMarkup(
                        merchant, repaired, 2L)
                        != RecoveredTradeController.deterministicMarkup(
                        merchant, repaired, 3L),
                net.minecraft.network.chat.Component.literal("Deux offres successives doivent pouvoir varier"));
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", templateNamespace = "vanillainstincts", template = "empty")
    public static void playerBrokenCropCanBeReplanted(GameTestHelper helper) {
        helper.assertTrue(FarmerController.isPlayerReplantCandidate(
                        Blocks.WHEAT.defaultBlockState()),
                net.minecraft.network.chat.Component.literal("Le blé cassé par un joueur doit devenir une cible"));
        helper.assertFalse(FarmerController.isPlayerReplantCandidate(
                        Blocks.STONE.defaultBlockState()),
                net.minecraft.network.chat.Component.literal("Un bloc ordinaire ne doit pas attirer le fermier"));
        helper.succeed();
    }
}

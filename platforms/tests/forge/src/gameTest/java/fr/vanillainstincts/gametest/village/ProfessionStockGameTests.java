package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.village.ClericBrewingController;
import fr.vanillainstincts.village.FarmerController;
import fr.vanillainstincts.village.ProfessionStockController;
import fr.vanillainstincts.village.RecoveredTradeController;
import fr.vanillainstincts.village.SmithRepairController;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class ProfessionStockGameTests {
    private ProfessionStockGameTests() {
    }

    @GameTest(batch = "profession_stock", template = "empty")
    public static void netherWartIsBrewingIngredient(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.isBrewingIngredient(
                        new ItemStack(Items.NETHER_WART)),
                "Le clerc doit reconnaître la verrue du Nether");
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", template = "empty")
    public static void wheatIsNotBrewingIngredient(GameTestHelper helper) {
        helper.assertFalse(ClericBrewingController.isBrewingIngredient(
                        new ItemStack(Items.WHEAT)),
                "Le blé ne doit pas être placé dans l'alambic");
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", template = "empty")
    public static void blazePowderIsBrewingFuel(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.isBrewingFuel(
                        new ItemStack(Items.BLAZE_POWDER)),
                "La poudre de Blaze doit alimenter l'alambic");
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", template = "empty")
    public static void potionIsBrewingContainer(GameTestHelper helper) {
        helper.assertTrue(ClericBrewingController.isPotionContainer(
                        new ItemStack(Items.POTION)),
                "Une potion doit occuper un emplacement de bouteille");
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", template = "empty")
    public static void clericRecoversBrewingIngredient(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfession.CLERIC,
                        new ItemStack(Items.SUGAR)),
                "Le clerc doit récupérer les ingrédients de potion");
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", template = "empty")
    public static void smithRecoversIronBlocks(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfession.ARMORER,
                        new ItemStack(Items.IRON_BLOCK)),
                "Le forgeron doit pouvoir stocker le fer de construction");
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", template = "empty")
    public static void farmerKeepsSeedReserve(GameTestHelper helper) {
        helper.assertValueEqual(ProfessionStockController.targetReserve(
                        VillagerProfession.FARMER,
                        new ItemStack(Items.WHEAT_SEEDS)), 24,
                "Le fermier doit constituer une réserve de graines");
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", template = "empty")
    public static void ironSwordUsesIronRepair(GameTestHelper helper) {
        helper.assertTrue(SmithRepairController.repairMaterial(
                        new ItemStack(Items.IRON_SWORD)) == Items.IRON_INGOT,
                "Une épée en fer doit consommer du fer pour être réparée");
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", template = "empty")
    public static void bowUsesStringRepair(GameTestHelper helper) {
        helper.assertTrue(SmithRepairController.repairMaterial(
                        new ItemStack(Items.BOW)) == Items.STRING,
                "Un arc doit consommer de la ficelle pour être réparé");
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", template = "empty")
    public static void repairAmountIsBounded(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(180);
        int amount = SmithRepairController.repairAmount(
                new UUID(13L, 13L), sword, 7L);
        helper.assertTrue(amount > 0 && amount <= 180,
                "La réparation doit restaurer une partie bornée des dégâts");
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", template = "empty")
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
                "Le meilleur état et le travail doivent augmenter le prix");
        helper.assertTrue(RecoveredTradeController.deterministicMarkup(
                        merchant, repaired, 2L)
                        != RecoveredTradeController.deterministicMarkup(
                        merchant, repaired, 3L),
                "Deux offres successives doivent pouvoir varier");
        helper.succeed();
    }

    @GameTest(batch = "profession_stock", template = "empty")
    public static void playerBrokenCropCanBeReplanted(GameTestHelper helper) {
        helper.assertTrue(FarmerController.isPlayerReplantCandidate(
                        Blocks.WHEAT.defaultBlockState()),
                "Le blé cassé par un joueur doit devenir une cible");
        helper.assertFalse(FarmerController.isPlayerReplantCandidate(
                        Blocks.STONE.defaultBlockState()),
                "Un bloc ordinaire ne doit pas attirer le fermier");
        helper.succeed();
    }
}

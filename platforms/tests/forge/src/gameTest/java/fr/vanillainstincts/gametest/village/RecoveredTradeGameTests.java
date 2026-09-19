package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.village.RecoveredTradeController;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class RecoveredTradeGameTests {
    private RecoveredTradeGameTests() {
    }

    @GameTest(batch = "recovered_trade", template = "empty")
    public static void smithAcceptsEquipment(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfession.WEAPONSMITH,
                        new ItemStack(Items.IRON_SWORD)),
                "Le forgeron doit récupérer les armes et équipements");
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", template = "empty")
    public static void smithRejectsFood(GameTestHelper helper) {
        helper.assertFalse(RecoveredTradeController.accepts(
                        VillagerProfession.TOOLSMITH,
                        new ItemStack(Items.BREAD)),
                "Le forgeron ne doit pas ramasser la nourriture");
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", template = "empty")
    public static void farmerAcceptsFood(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfession.FARMER,
                        new ItemStack(Items.COOKED_BEEF)),
                "Le fermier doit pouvoir revendre la nourriture trouvée");
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", template = "empty")
    public static void farmerAcceptsEggs(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfession.FARMER,
                        new ItemStack(Items.EGG, 8)),
                "Le fermier doit pouvoir récupérer les œufs");
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", template = "empty")
    public static void fishermanAcceptsVanillaTreasure(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfession.FISHERMAN,
                        new ItemStack(Items.BOW)),
                "Un arc faisant partie de la pêche vanilla doit être accepté");
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", template = "empty")
    public static void fishermanRejectsImpossibleEquipment(GameTestHelper helper) {
        helper.assertFalse(RecoveredTradeController.accepts(
                        VillagerProfession.FISHERMAN,
                        new ItemStack(Items.DIAMOND_SWORD)),
                "Le pêcheur ne doit pas récupérer un équipement hors table de pêche");
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", template = "empty")
    public static void clericAcceptsPlayerWrittenBook(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfession.CLERIC,
                        new ItemStack(Items.WRITTEN_BOOK)),
                "Le clerc doit récupérer les livres écrits par les joueurs");
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", template = "empty")
    public static void wanderingTraderRejectsAdminItems(GameTestHelper helper) {
        helper.assertFalse(RecoveredTradeController.usefulToWanderingTrader(
                        new ItemStack(Items.COMMAND_BLOCK)),
                "Le marchand ambulant ne doit pas revendre les objets administratifs");
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", template = "empty")
    public static void wanderingTraderAcceptsUsefulGoods(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.usefulToWanderingTrader(
                        new ItemStack(Items.DIAMOND)),
                "Le marchand ambulant doit récupérer toute marchandise utile");
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", template = "empty")
    public static void markupStaysAnnoyingButBounded(GameTestHelper helper) {
        double markup = RecoveredTradeController.deterministicMarkup(
                new UUID(12L, 34L), new ItemStack(Items.BREAD));
        helper.assertTrue(markup >= 1.25D && markup <= 1.75D,
                "La marge doit rester entre 25 % et 75 %");
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", template = "empty")
    public static void recoveredQuantityChangesPrice(GameTestHelper helper) {
        UUID merchant = new UUID(56L, 78L);
        int one = RecoveredTradeController.emeraldPrice(merchant,
                new ItemStack(Items.CARROT, 1));
        int stack = RecoveredTradeController.emeraldPrice(merchant,
                new ItemStack(Items.CARROT, 64));
        helper.assertTrue(stack > one,
                "Une grande quantité doit coûter plus qu'un objet isolé");
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", template = "empty")
    public static void recoveredTradePreservesExactStack(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        ItemStack named = new ItemStack(Items.IRON_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Épée retrouvée"));
        ItemEntity item = new ItemEntity(helper.getLevel(), villager.getX(),
                villager.getY(), villager.getZ(), named.copy());
        helper.getLevel().addFreshEntity(item);
        helper.assertTrue(RecoveredTradeController.recover(villager, item),
                "L'objet réel doit devenir une offre");
        ItemStack result = villager.getOffers()
                .get(villager.getOffers().size() - 1).getResult();
        helper.assertTrue(ItemStack.isSameItemSameComponents(named, result),
                "Le nom et les composants de l'objet doivent être conservés");
        helper.succeed();
    }
}

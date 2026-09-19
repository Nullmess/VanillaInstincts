package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.village.VillagerProfessionCompat;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.village.RecoveredTradeController;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecoveredTradeGameTests {
    private RecoveredTradeGameTests() {
    }

    @GameTest(batch = "recovered_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void smithAcceptsEquipment(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH),
                        new ItemStack(Items.IRON_SWORD)),
                net.minecraft.network.chat.Component.literal("Le forgeron doit récupérer les armes et équipements"));
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void smithRejectsFood(GameTestHelper helper) {
        helper.assertFalse(RecoveredTradeController.accepts(
                        VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH),
                        new ItemStack(Items.BREAD)),
                net.minecraft.network.chat.Component.literal("Le forgeron ne doit pas ramasser la nourriture"));
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerAcceptsFood(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfessionCompat.value(VillagerProfession.FARMER),
                        new ItemStack(Items.COOKED_BEEF)),
                net.minecraft.network.chat.Component.literal("Le fermier doit pouvoir revendre la nourriture trouvée"));
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerAcceptsEggs(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfessionCompat.value(VillagerProfession.FARMER),
                        new ItemStack(Items.EGG, 8)),
                net.minecraft.network.chat.Component.literal("Le fermier doit pouvoir récupérer les œufs"));
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void fishermanAcceptsVanillaTreasure(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfessionCompat.value(VillagerProfession.FISHERMAN),
                        new ItemStack(Items.BOW)),
                net.minecraft.network.chat.Component.literal("Un arc faisant partie de la pêche vanilla doit être accepté"));
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void fishermanRejectsImpossibleEquipment(GameTestHelper helper) {
        helper.assertFalse(RecoveredTradeController.accepts(
                        VillagerProfessionCompat.value(VillagerProfession.FISHERMAN),
                        new ItemStack(Items.DIAMOND_SWORD)),
                net.minecraft.network.chat.Component.literal("Le pêcheur ne doit pas récupérer un équipement hors table de pêche"));
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void clericAcceptsPlayerWrittenBook(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.accepts(
                        VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                        new ItemStack(Items.WRITTEN_BOOK)),
                net.minecraft.network.chat.Component.literal("Le clerc doit récupérer les livres écrits par les joueurs"));
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void wanderingTraderRejectsAdminItems(GameTestHelper helper) {
        helper.assertFalse(RecoveredTradeController.usefulToWanderingTrader(
                        new ItemStack(Items.COMMAND_BLOCK)),
                net.minecraft.network.chat.Component.literal("Le marchand ambulant ne doit pas revendre les objets administratifs"));
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void wanderingTraderAcceptsUsefulGoods(GameTestHelper helper) {
        helper.assertTrue(RecoveredTradeController.usefulToWanderingTrader(
                        new ItemStack(Items.DIAMOND)),
                net.minecraft.network.chat.Component.literal("Le marchand ambulant doit récupérer toute marchandise utile"));
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void markupStaysAnnoyingButBounded(GameTestHelper helper) {
        double markup = RecoveredTradeController.deterministicMarkup(
                new UUID(12L, 34L), new ItemStack(Items.BREAD));
        helper.assertTrue(markup >= 1.25D && markup <= 1.75D,
                net.minecraft.network.chat.Component.literal("La marge doit rester entre 25 % et 75 %"));
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void recoveredQuantityChangesPrice(GameTestHelper helper) {
        UUID merchant = new UUID(56L, 78L);
        int one = RecoveredTradeController.emeraldPrice(merchant,
                new ItemStack(Items.CARROT, 1));
        int stack = RecoveredTradeController.emeraldPrice(merchant,
                new ItemStack(Items.CARROT, 64));
        helper.assertTrue(stack > one,
                net.minecraft.network.chat.Component.literal("Une grande quantité doit coûter plus qu'un objet isolé"));
        helper.succeed();
    }

    @GameTest(batch = "recovered_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void recoveredTradePreservesExactStack(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        ItemStack named = new ItemStack(Items.IRON_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Épée retrouvée"));
        ItemEntity item = new ItemEntity(helper.getLevel(), villager.getX(),
                villager.getY(), villager.getZ(), named.copy());
        helper.getLevel().addFreshEntity(item);
        helper.assertTrue(RecoveredTradeController.recover(villager, item),
                net.minecraft.network.chat.Component.literal("L'objet réel doit devenir une offre"));
        ItemStack result = villager.getOffers()
                .get(villager.getOffers().size() - 1).getResult();
        helper.assertTrue(ItemStack.isSameItemSameComponents(named, result),
                net.minecraft.network.chat.Component.literal("Le nom et les composants de l'objet doivent être conservés"));
        helper.succeed();
    }
}

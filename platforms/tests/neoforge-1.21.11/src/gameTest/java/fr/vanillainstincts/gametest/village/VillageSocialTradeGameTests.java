package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.village.VillagerProfessionCompat;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.village.GolemRepairController;
import fr.vanillainstincts.village.RecoveredTradeController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import java.util.UUID;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageSocialTradeGameTests {
    private VillageSocialTradeGameTests() {
    }

    @GameTest(batch = "village_social_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void repeatedSocialExchangesAreAllowed(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController.mayExchangeToday(0),
                net.minecraft.network.chat.Component.literal("Le premier échange quotidien doit être possible"));
        helper.assertTrue(VillagerFoodExchangeController.mayExchangeToday(12),
                net.minecraft.network.chat.Component.literal("La période commerciale ne doit plus s'arrêter après trois échanges"));
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void safetyLimitStillExists(GameTestHelper helper) {
        helper.assertFalse(VillagerFoodExchangeController.mayExchangeToday(64),
                net.minecraft.network.chat.Component.literal("Une limite technique élevée doit empêcher une boucle infinie"));
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void senderCannotRecoverOwnGift(GameTestHelper helper) {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        helper.assertFalse(VillagerFoodExchangeController
                        .intendedVillagerMayCollect(sender, sender, recipient, false),
                net.minecraft.network.chat.Component.literal("Le donneur ne doit jamais récupérer le bien qu'il vient de jeter"));
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void recipientCanRecoverGift(GameTestHelper helper) {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        helper.assertTrue(VillagerFoodExchangeController
                        .intendedVillagerMayCollect(recipient, sender, recipient, false),
                net.minecraft.network.chat.Component.literal("Seul le destinataire doit recevoir le bien"));
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void sellerCanRecoverEmeraldPayment(GameTestHelper helper) {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        helper.assertTrue(VillagerFoodExchangeController
                        .intendedVillagerMayCollect(sender, sender, recipient, true),
                net.minecraft.network.chat.Component.literal("Le vendeur doit récupérer uniquement le paiement en émeraudes"));
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void samePartnerMustWait(GameTestHelper helper) {
        helper.assertFalse(VillagerFoodExchangeController
                        .partnerReady(8_000L, 7_999L),
                net.minecraft.network.chat.Component.literal("Le même partenaire ne doit pas être sollicité immédiatement"));
        helper.assertTrue(VillagerFoodExchangeController
                        .partnerReady(8_000L, 8_000L),
                net.minecraft.network.chat.Component.literal("Un partenaire redevient disponible après son délai"));
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerCanExchangeFood(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController
                        .isProfessionExchangeGood(VillagerProfessionCompat.value(VillagerProfession.FARMER),
                                new ItemStack(Items.BREAD, 12)),
                net.minecraft.network.chat.Component.literal("Le fermier doit pouvoir partager son surplus alimentaire"));
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void fletcherCanExchangeArrows(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController
                        .isProfessionExchangeGood(VillagerProfessionCompat.value(VillagerProfession.FLETCHER),
                                new ItemStack(Items.ARROW, 16)),
                net.minecraft.network.chat.Component.literal("Le fabricant de flèches doit participer aux échanges sociaux"));
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void clericCanExchangeProducedPotions(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController
                        .isProfessionExchangeGood(VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                                new ItemStack(Items.POTION)),
                net.minecraft.network.chat.Component.literal("Le clerc doit pouvoir proposer une potion réellement produite"));
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void rareBookCostsMoreThanPlainBook(GameTestHelper helper) {
        UUID merchant = UUID.randomUUID();
        int plain = RecoveredTradeController.emeraldPrice(merchant,
                new ItemStack(Items.BOOK));
        int enchanted = RecoveredTradeController.emeraldPrice(merchant,
                new ItemStack(Items.ENCHANTED_BOOK));
        helper.assertTrue(enchanted > plain,
                net.minecraft.network.chat.Component.literal("Un livre enchanté doit être estimé au-dessus d'un livre ordinaire"));
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void damagedEquipmentLosesValue(GameTestHelper helper) {
        ItemStack intact = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack damaged = intact.copy();
        damaged.setDamageValue(damaged.getMaxDamage() - 1);
        helper.assertTrue(RecoveredTradeController.baseUnitValue(intact)
                        > RecoveredTradeController.baseUnitValue(damaged),
                net.minecraft.network.chat.Component.literal("L'état réel de l'équipement doit influencer son prix"));
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", templateNamespace = "vanillainstincts", template = "empty")
    public static void masonRepairsAndTheftRequiresOneKill(GameTestHelper helper) {
        helper.assertTrue(GolemRepairController
                        .isRepairProfession(VillagerProfessionCompat.value(VillagerProfession.MASON)),
                net.minecraft.network.chat.Component.literal("Le maçon doit pouvoir sauver un golem blessé"));
        helper.assertValueEqual(GolemRepairController
                        .ingotsNeededAfterTheft(2), 3,
                net.minecraft.network.chat.Component.literal("Un lingot volé doit être remplacé avant la fin des réparations"));
        helper.assertTrue(GolemRepairController.retaliationSatisfied(1),
                net.minecraft.network.chat.Component.literal("Une mort du voleur doit permettre au golem de reprendre sa réparation"));
        helper.succeed();
    }
}

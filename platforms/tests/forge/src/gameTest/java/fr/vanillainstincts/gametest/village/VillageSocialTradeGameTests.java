package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.village.GolemRepairController;
import fr.vanillainstincts.village.RecoveredTradeController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class VillageSocialTradeGameTests {
    private VillageSocialTradeGameTests() {
    }

    @GameTest(batch = "village_social_trade", template = "empty")
    public static void repeatedSocialExchangesAreAllowed(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController.mayExchangeToday(0),
                "Le premier échange quotidien doit être possible");
        helper.assertTrue(VillagerFoodExchangeController.mayExchangeToday(12),
                "La période commerciale ne doit plus s'arrêter après trois échanges");
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", template = "empty")
    public static void safetyLimitStillExists(GameTestHelper helper) {
        helper.assertFalse(VillagerFoodExchangeController.mayExchangeToday(64),
                "Une limite technique élevée doit empêcher une boucle infinie");
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", template = "empty")
    public static void senderCannotRecoverOwnGift(GameTestHelper helper) {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        helper.assertFalse(VillagerFoodExchangeController
                        .intendedVillagerMayCollect(sender, sender, recipient, false),
                "Le donneur ne doit jamais récupérer le bien qu'il vient de jeter");
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", template = "empty")
    public static void recipientCanRecoverGift(GameTestHelper helper) {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        helper.assertTrue(VillagerFoodExchangeController
                        .intendedVillagerMayCollect(recipient, sender, recipient, false),
                "Seul le destinataire doit recevoir le bien");
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", template = "empty")
    public static void sellerCanRecoverEmeraldPayment(GameTestHelper helper) {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        helper.assertTrue(VillagerFoodExchangeController
                        .intendedVillagerMayCollect(sender, sender, recipient, true),
                "Le vendeur doit récupérer uniquement le paiement en émeraudes");
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", template = "empty")
    public static void samePartnerMustWait(GameTestHelper helper) {
        helper.assertFalse(VillagerFoodExchangeController
                        .partnerReady(8_000L, 7_999L),
                "Le même partenaire ne doit pas être sollicité immédiatement");
        helper.assertTrue(VillagerFoodExchangeController
                        .partnerReady(8_000L, 8_000L),
                "Un partenaire redevient disponible après son délai");
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", template = "empty")
    public static void farmerCanExchangeFood(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController
                        .isProfessionExchangeGood(VillagerProfession.FARMER,
                                new ItemStack(Items.BREAD, 12)),
                "Le fermier doit pouvoir partager son surplus alimentaire");
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", template = "empty")
    public static void fletcherCanExchangeArrows(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController
                        .isProfessionExchangeGood(VillagerProfession.FLETCHER,
                                new ItemStack(Items.ARROW, 16)),
                "Le fabricant de flèches doit participer aux échanges sociaux");
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", template = "empty")
    public static void clericCanExchangeProducedPotions(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController
                        .isProfessionExchangeGood(VillagerProfession.CLERIC,
                                new ItemStack(Items.POTION)),
                "Le clerc doit pouvoir proposer une potion réellement produite");
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", template = "empty")
    public static void rareBookCostsMoreThanPlainBook(GameTestHelper helper) {
        UUID merchant = UUID.randomUUID();
        int plain = RecoveredTradeController.emeraldPrice(merchant,
                new ItemStack(Items.BOOK));
        int enchanted = RecoveredTradeController.emeraldPrice(merchant,
                new ItemStack(Items.ENCHANTED_BOOK));
        helper.assertTrue(enchanted > plain,
                "Un livre enchanté doit être estimé au-dessus d'un livre ordinaire");
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", template = "empty")
    public static void damagedEquipmentLosesValue(GameTestHelper helper) {
        ItemStack intact = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack damaged = intact.copy();
        damaged.setDamageValue(damaged.getMaxDamage() - 1);
        helper.assertTrue(RecoveredTradeController.baseUnitValue(intact)
                        > RecoveredTradeController.baseUnitValue(damaged),
                "L'état réel de l'équipement doit influencer son prix");
        helper.succeed();
    }

    @GameTest(batch = "village_social_trade", template = "empty")
    public static void masonRepairsAndTheftRequiresOneKill(GameTestHelper helper) {
        helper.assertTrue(GolemRepairController
                        .isRepairProfession(VillagerProfession.MASON),
                "Le maçon doit pouvoir sauver un golem blessé");
        helper.assertValueEqual(GolemRepairController
                        .ingotsNeededAfterTheft(2), 3,
                "Un lingot volé doit être remplacé avant la fin des réparations");
        helper.assertTrue(GolemRepairController.retaliationSatisfied(1),
                "Une mort du voleur doit permettre au golem de reprendre sa réparation");
        helper.succeed();
    }
}

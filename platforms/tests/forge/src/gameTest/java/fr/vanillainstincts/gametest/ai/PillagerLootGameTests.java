package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.PillagerOutpostLootController;
import fr.vanillainstincts.core.rules.PillagerRules;
import fr.vanillainstincts.village.RecoveredTradeController;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class PillagerLootGameTests {
    private PillagerLootGameTests() {
    }

    @GameTest(batch = "pillager_loot", template = "empty")
    public static void everyPlayerRecoveredOfferHasOneUse(GameTestHelper helper) {
        Villager merchant = helper.spawn(EntityType.VILLAGER,
                new net.minecraft.core.BlockPos(2, 1, 2));
        ItemEntity deathDrop = new ItemEntity(helper.getLevel(),
                merchant.getX(), merchant.getY(), merchant.getZ(),
                new ItemStack(Items.DIAMOND, 32));
        helper.getLevel().addFreshEntity(deathDrop);
        RecoveredTradeController.markPlayerDeathDrop(deathDrop);
        helper.assertTrue(RecoveredTradeController.recover(merchant, deathDrop),
                "Le marchand doit récupérer la pile du joueur mort");
        MerchantOffer offer = merchant.getOffers().getFirst();
        helper.assertValueEqual(offer.getResult().getCount(), 32,
                "La pile de mort doit être rendue entière, sans apprentissage unitaire");
        helper.assertValueEqual(offer.getMaxUses(), 1,
                "Le stuff récupéré ne doit être payable qu'une fois");
        helper.assertTrue(RecoveredTradeController.consumePlayerRecoveredOffer(
                        merchant, offer) && merchant.getOffers().isEmpty(),
                "Après paiement, l'offre doit disparaître immédiatement");
        helper.assertFalse(RecoveredTradeController.consumePlayerRecoveredOffer(
                        merchant, offer),
                "La même récupération ne doit jamais être payable deux fois");

        ItemEntity manualDrop = new ItemEntity(helper.getLevel(),
                merchant.getX(), merchant.getY(), merchant.getZ(),
                new ItemStack(Items.COBBLESTONE, 16));
        helper.getLevel().addFreshEntity(manualDrop);
        RecoveredTradeController.markPlayerToss(manualDrop);
        helper.assertTrue(RecoveredTradeController.recover(merchant, manualDrop),
                "Le marchand doit aussi récupérer une pile jetée manuellement");
        MerchantOffer tossedOffer = merchant.getOffers().getFirst();
        helper.assertValueEqual(tossedOffer.getResult().getCount(), 16,
                "Le drop manuel doit rester une pile exacte, sans offre apprise");
        helper.assertValueEqual(tossedOffer.getMaxUses(), 1,
                "Un drop manuel du joueur ne doit être payable qu'une fois");
        helper.assertTrue(RecoveredTradeController.consumePlayerRecoveredOffer(
                        merchant, tossedOffer) && merchant.getOffers().isEmpty(),
                "L'offre du drop manuel doit disparaître après le paiement");
        helper.assertFalse(RecoveredTradeController.consumePlayerRecoveredOffer(
                        merchant, tossedOffer),
                "Le drop manuel ne doit jamais être payable une seconde fois");
        helper.succeed();
    }

    @GameTest(batch = "pillager_loot", template = "empty")
    public static void naturalRecoveredOfferKeepsStock(GameTestHelper helper) {
        helper.assertValueEqual(RecoveredTradeController
                .recoveredOfferMaxUses(false), Integer.MAX_VALUE,
                "Une trouvaille naturelle ne doit pas être confondue avec le bien du joueur");
        helper.succeed();
    }

    @GameTest(batch = "pillager_loot", template = "empty")
    public static void everyPlayerStackMayBeLooted(GameTestHelper helper) {
        helper.assertTrue(PillagerOutpostLootController
                        .shouldReservePlayerDrop(new ItemStack(Items.DIAMOND_SWORD)),
                "Les pillards doivent accepter tout équipement du joueur");
        helper.succeed();
    }

    @GameTest(batch = "pillager_loot", template = "empty")
    public static void emptyStackIsNotLoot(GameTestHelper helper) {
        helper.assertFalse(PillagerOutpostLootController
                        .shouldReservePlayerDrop(ItemStack.EMPTY),
                "Une pile vide ne doit pas créer une mission de pillage");
        helper.succeed();
    }

    @GameTest(batch = "pillager_loot", template = "empty")
    public static void darkOakCountsAsOutpostMaterial(GameTestHelper helper) {
        helper.assertTrue(PillagerOutpostLootController.isOutpostMaterial(
                        Blocks.DARK_OAK_PLANKS.defaultBlockState()),
                "Le bois sombre doit participer à la signature de l'avant-poste");
        helper.succeed();
    }

    @GameTest(batch = "pillager_loot", template = "empty")
    public static void cobblestoneCountsAsOutpostMaterial(GameTestHelper helper) {
        helper.assertTrue(PillagerOutpostLootController.isOutpostMaterial(
                        Blocks.COBBLESTONE.defaultBlockState()),
                "La pierre de l'avant-poste doit être reconnue");
        helper.succeed();
    }

    @GameTest(batch = "pillager_loot", template = "empty")
    public static void unrelatedBlockIsRejected(GameTestHelper helper) {
        helper.assertFalse(PillagerOutpostLootController.isOutpostMaterial(
                        Blocks.SAND.defaultBlockState()),
                "Un coffre isolé dans le désert ne doit pas devenir un avant-poste");
        helper.succeed();
    }

    @GameTest(batch = "pillager_loot", template = "empty")
    public static void outpostScoreUsesThreshold(GameTestHelper helper) {
        helper.assertFalse(PillagerOutpostLootController.isOutpostChestScore(
                        PillagerRules.OUTPOST_CHEST_MATERIAL_THRESHOLD - 1),
                "Une structure trop faible doit être refusée");
        helper.assertTrue(PillagerOutpostLootController.isOutpostChestScore(
                        PillagerRules.OUTPOST_CHEST_MATERIAL_THRESHOLD),
                "Le seuil exact doit identifier le coffre d'avant-poste");
        helper.succeed();
    }

    @GameTest(batch = "pillager_loot", template = "empty")
    public static void activeClaimDoesNotExpireEarly(GameTestHelper helper) {
        helper.assertFalse(PillagerOutpostLootController.assignmentExpired(
                        100L, 100L + PillagerRules.OUTPOST_LOOT_CLAIM_TIMEOUT_TICKS),
                "Un pillard actif ne doit pas perdre sa pile prématurément");
        helper.succeed();
    }

    @GameTest(batch = "pillager_loot", template = "empty")
    public static void abandonedClaimCanBeRecovered(GameTestHelper helper) {
        helper.assertTrue(PillagerOutpostLootController.assignmentExpired(
                        100L, 101L + PillagerRules.OUTPOST_LOOT_CLAIM_TIMEOUT_TICKS),
                "Une pile abandonnée doit pouvoir être reprise par un autre pillard");
        helper.succeed();
    }

    @GameTest(batch = "pillager_loot", template = "empty")
    public static void chestReceivesExactStack(GameTestHelper helper) {
        SimpleContainer chest = new SimpleContainer(2);
        ItemStack input = new ItemStack(Items.DIAMOND, 17);
        ItemStack remainder = PillagerOutpostLootController.insert(chest, input);
        helper.assertTrue(remainder.isEmpty(),
                "Le coffre vide doit recevoir toute la pile");
        helper.assertValueEqual(chest.getItem(0).getCount(), 17,
                "La quantité déposée doit rester exacte");
        helper.succeed();
    }

    @GameTest(batch = "pillager_loot", template = "empty")
    public static void fullChestReturnsOnlyOverflow(GameTestHelper helper) {
        SimpleContainer chest = new SimpleContainer(1);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 63));
        ItemStack remainder = PillagerOutpostLootController.insert(chest,
                new ItemStack(Items.DIAMOND, 4));
        helper.assertValueEqual(chest.getItem(0).getCount(), 64,
                "Le coffre doit être rempli sans dépasser la limite");
        helper.assertValueEqual(remainder.getCount(), 3,
                "Seul le surplus réel doit rester au-dessus du coffre");
        helper.succeed();
    }
}

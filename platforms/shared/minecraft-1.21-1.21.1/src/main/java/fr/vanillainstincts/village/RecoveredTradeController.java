package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.PillagerOutpostLootController;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.ProfessionRules;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
/**
 * Transforme les objets réellement trouvés au sol en offres de revente. Le
 * résultat conserve tous ses composants : enchantements, nom, auteur, pages,
 * dégâts et autres données ajoutées par Minecraft ou un autre mod. Toute pile
 * provenant d'un joueur, jetée manuellement ou perdue à sa mort, est rendue
 * entière par une offre à usage unique immédiatement supprimée après paiement.
 */
public final class RecoveredTradeController {
    private static final String PLAYER_RECOVERED_OFFER_INDICES =
            "vanillainstincts_player_recovered_offer_indices";
    private static final String PLAYER_TOSSED_ITEM =
            "vanillainstincts_player_tossed_item";
    private static final String PLAYER_DEATH_DROP =
            "vanillainstincts_player_death_drop";
    private RecoveredTradeController() {
    }

    public static boolean contribute(Villager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerLevel level, long gameTime) {
        if (villager == null || villager.isBaby() || villager.isTrading()
                || state.danger(gameTime) != null
                || !scanNow(villager, gameTime)) {
            return false;
        }
        VillagerProfession profession = villager.getVillagerData()
                .getProfession();
        if (profession == VillagerProfession.NONE
                || profession == VillagerProfession.NITWIT) {
            return false;
        }
        ItemEntity item = nearestAccepted(villager, level, profession, false);
        if (item == null) return false;

        if (villager.distanceToSqr(item)
                > ProfessionRules.RECOVERED_TRADE_REACH_SQR) {
            plan.offerNavigation(VanillaInstinctsState.RECOVERED_TRADE,
                    ActionOwner.VILLAGER_PROFESSION,
                    ProfessionRules.PRIORITY_RECOVERED_TRADE,
                    item.position(), ProfessionRules.RECOVERED_TRADE_SPEED,
                    ProfessionRules.STATE_HOLD_RECOVERED_TRADE_TICKS, null);
            return true;
        }

        plan.offerSpecial(VanillaInstinctsState.RECOVERED_TRADE,
                ActionOwner.VILLAGER_PROFESSION,
                ProfessionRules.PRIORITY_RECOVERED_TRADE + 2,
                ProfessionRules.STATE_HOLD_RECOVERED_TRADE_TICKS,
                () -> recover(villager, item));
        return true;
    }

    /** Le marchand ambulant est le commerçant de caravane généraliste. */
    public static boolean maintain(WanderingTrader trader, ServerLevel level,
                                   long gameTime) {
        if (trader == null || level == null || trader.isTrading()
                || !scanNow(trader, gameTime)) {
            return false;
        }
        ItemEntity item = nearestAccepted(trader, level, null, true);
        if (item == null) return false;
        if (trader.distanceToSqr(item)
                > ProfessionRules.RECOVERED_TRADE_REACH_SQR) {
            trader.getNavigation().moveTo(item.getX(), item.getY(), item.getZ(),
                    ProfessionRules.RECOVERED_TRADE_SPEED);
            return true;
        }
        return recover(trader, item);
    }

    private static boolean scanNow(AbstractVillager merchant, long gameTime) {
        return Math.floorMod(gameTime + merchant.getId() * 23L,
                ProfessionRules.RECOVERED_TRADE_SCAN_INTERVAL_TICKS) == 0L;
    }

    private static ItemEntity nearestAccepted(AbstractVillager merchant,
                                               ServerLevel level,
                                               VillagerProfession profession,
                                               boolean wandering) {
        double radius = ProfessionRules.RECOVERED_TRADE_SCAN_RADIUS;
        return level.getEntitiesOfClass(ItemEntity.class,
                        merchant.getBoundingBox().inflate(radius, 16.0D, radius),
                        entity -> entity.isAlive() && !entity.hasPickUpDelay()
                                && !VillagerFoodExchangeController
                                .isVillageOrigin(entity)
                                && !PillagerOutpostLootController
                                .isReservedLoot(entity)
                                && (wandering
                                ? usefulToWanderingTrader(entity.getItem())
                                : accepts(profession, entity.getItem())))
                .stream()
                .min(Comparator.comparingDouble(merchant::distanceToSqr))
                .orElse(null);
    }

    public static boolean recover(AbstractVillager merchant, ItemEntity entity) {
        if (merchant == null || entity == null || !entity.isAlive()) {
            return false;
        }
        ItemStack found = entity.getItem();
        if (found.isEmpty()) return false;
        boolean playerOrigin = isPlayerOrigin(entity);
        MerchantOffers offers = merchant.getOffers();

        ItemStack result = found.copy();
        long offerSalt = nextOfferSalt(merchant);
        int workmanship = 0;
        if (!playerOrigin && merchant instanceof Villager villager) {
            result = ProfessionStockController.reserveForWork(villager,
                    result);
            if (!result.isEmpty()) {
                SmithRepairController.RepairResult repair =
                        SmithRepairController.maybeRepair(villager, result,
                                offerSalt);
                result = repair.stack();
                workmanship = repair.workmanship();
            }
        }
        if (result.isEmpty()) {
            merchant.swing(InteractionHand.MAIN_HAND);
            entity.discard();
            return true;
        }

        int maxUses = recoveredOfferMaxUses(playerOrigin);
        if (!playerOrigin && containsExactResult(offers, result)) {
            entity.discard();
            return true;
        }
        if (offers.size() >= ProfessionRules.MAX_RECOVERED_TRADE_OFFERS) {
            return false;
        }
        int price = pricedFor(merchant, result, offerSalt, workmanship);
        MerchantOffer recoveredOffer = new MerchantOffer(
                new ItemCost(Items.EMERALD, price), result, maxUses,
                ProfessionRules.RECOVERED_TRADE_XP,
                ProfessionRules.RECOVERED_TRADE_PRICE_MULTIPLIER);
        int incidentScore = merchant.getPersistentData()
                .getInt("vanillainstincts_price_incident_score");
        if (incidentScore > 0) {
            recoveredOffer.setSpecialPriceDiff(Math.min(
                    ProfessionRules.VILLAGER_PRICE_PENALTY_MAX, incidentScore));
        }
        offers.add(recoveredOffer);
        if (playerOrigin) {
            markPlayerRecoveredOffer(merchant, offers.size() - 1);
        }
        merchant.swing(InteractionHand.MAIN_HAND);
        entity.discard();
        return true;
    }

    /** Marque sans ambiguïté un objet créé par l'action de jeter du joueur. */
    public static void markPlayerToss(ItemEntity entity, Player player) {
        if (entity == null || player == null) return;
        markPlayerToss(entity);
        entity.setThrower(player);
    }

    /** Variante utilisée lorsque l'origine joueur est déjà garantie. */
    public static void markPlayerToss(ItemEntity entity) {
        if (entity == null) return;
        entity.getPersistentData().putBoolean(PLAYER_TOSSED_ITEM, true);
    }

    /** Marque une pile créée par la mort du joueur, sans changer son contenu. */
    public static void markPlayerDeathDrop(ItemEntity entity, Player player) {
        if (player == null) return;
        markPlayerDeathDrop(entity);
    }

    public static void markPlayerDeathDrop(ItemEntity entity) {
        if (entity == null) return;
        entity.getPersistentData().putBoolean(PLAYER_DEATH_DROP, true);
    }

    public static boolean isPlayerDeathDrop(ItemEntity entity) {
        return entity != null && entity.getPersistentData()
                .getBoolean(PLAYER_DEATH_DROP);
    }

    public static boolean isPlayerOrigin(ItemEntity entity) {
        return entity != null && (entity.getPersistentData()
                .getBoolean(PLAYER_TOSSED_ITEM)
                || entity.getPersistentData().getBoolean(PLAYER_DEATH_DROP)
                || entity.getOwner() instanceof Player);
    }

    /** Compatibilité avec les anciens tests et appels. */
    public static boolean isPlayerTossed(ItemEntity entity) {
        return isPlayerOrigin(entity);
    }

    /**
     * Supprime immédiatement une offre issue d'un objet provenant d'un joueur.
     * Les indices sont stockés dans les données persistantes du marchand afin
     * que la protection anti-duplication survive à une sauvegarde/reconnexion.
     */
    public static int recoveredOfferMaxUses(boolean playerOrigin) {
        return playerOrigin ? 1 : Integer.MAX_VALUE;
    }

    public static boolean consumePlayerRecoveredOffer(
            AbstractVillager merchant, MerchantOffer tradedOffer) {
        if (merchant == null || tradedOffer == null) return false;
        MerchantOffers offers = merchant.getOffers();
        int index = -1;
        for (int candidate = 0; candidate < offers.size(); candidate++) {
            if (offers.get(candidate) == tradedOffer) {
                index = candidate;
                break;
            }
        }
        if (index < 0 || !isPlayerRecoveredOffer(merchant, index)) {
            return false;
        }
        offers.remove(index);
        shiftPlayerRecoveredIndicesAfterRemoval(merchant, index);
        return true;
    }

    private static void markPlayerRecoveredOffer(AbstractVillager merchant,
                                                  int index) {
        int[] previous = merchant.getPersistentData()
                .getIntArray(PLAYER_RECOVERED_OFFER_INDICES);
        int[] updated = java.util.Arrays.copyOf(previous, previous.length + 1);
        updated[previous.length] = index;
        merchant.getPersistentData().putIntArray(
                PLAYER_RECOVERED_OFFER_INDICES, updated);
    }

    private static boolean isPlayerRecoveredOffer(AbstractVillager merchant,
                                                   int index) {
        for (int stored : merchant.getPersistentData()
                .getIntArray(PLAYER_RECOVERED_OFFER_INDICES)) {
            if (stored == index) return true;
        }
        return false;
    }

    private static void shiftPlayerRecoveredIndicesAfterRemoval(
            AbstractVillager merchant, int removedIndex) {
        int[] previous = merchant.getPersistentData()
                .getIntArray(PLAYER_RECOVERED_OFFER_INDICES);
        int kept = 0;
        for (int stored : previous) {
            if (stored != removedIndex) kept++;
        }
        int[] updated = new int[kept];
        int cursor = 0;
        for (int stored : previous) {
            if (stored == removedIndex) continue;
            updated[cursor++] = stored > removedIndex ? stored - 1 : stored;
        }
        merchant.getPersistentData().putIntArray(
                PLAYER_RECOVERED_OFFER_INDICES, updated);
    }

    /**
     * Ajoute à la marchandise un objet réellement produit par le métier.
     * Contrairement aux objets trouvés au sol, l'offre possède un stock borné
     * afin que la production visible serve réellement de réapprovisionnement.
     */
    public static boolean addProducedOffer(Villager merchant,
                                           ItemStack produced) {
        return addProducedOffer(merchant, produced,
                ProfessionRules.CLERIC_PRODUCED_TRADE_USES);
    }

    /**
     * Ajoute exactement autant d'utilisations que de produits physiquement
     * récupérés. Cela permet au clerc de réserver parfois une bouteille aux
     * échanges sociaux sans créer trois ventes à partir de seulement deux
     * bouteilles restantes.
     */
    public static boolean addProducedOffer(Villager merchant,
                                           ItemStack produced, int uses) {
        return addProducedOffer(merchant, produced, uses, 1);
    }

    public static boolean addProducedOffer(Villager merchant,
                                           ItemStack produced, int uses,
                                           int workmanship) {
        if (merchant == null || produced == null || produced.isEmpty()
                || uses <= 0) {
            return false;
        }
        MerchantOffers offers = merchant.getOffers();
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer existing = offers.get(index);
            ItemStack result = existing.getResult();
            if (ItemStack.isSameItemSameComponents(result, produced)) {
                // Une offre encore disponible représente déjà un stock réel.
                // Le sélecteur de recettes évite normalement ce cas ; on ne
                // fait surtout pas disparaître un nouveau lot derrière elle.
                if (!existing.isOutOfStock()) return false;
                offers.remove(index);
                shiftPlayerRecoveredIndicesAfterRemoval(merchant, index);
                break;
            }
        }
        if (offers.size() >= ProfessionRules.MAX_RECOVERED_TRADE_OFFERS) {
            return false;
        }
        ItemStack result = produced.copy();
        long offerSalt = nextOfferSalt(merchant);
        int price = pricedFor(merchant, result, offerSalt, workmanship);
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, price), result, uses,
                ProfessionRules.RECOVERED_TRADE_XP,
                ProfessionRules.RECOVERED_TRADE_PRICE_MULTIPLIER);
        int incidentScore = merchant.getPersistentData()
                .getInt("vanillainstincts_price_incident_score");
        if (incidentScore > 0) {
            offer.setSpecialPriceDiff(Math.min(
                    ProfessionRules.VILLAGER_PRICE_PENALTY_MAX, incidentScore));
        }
        offers.add(offer);
        return true;
    }

    /**
     * Indique qu'un produit identique possède encore du stock marchand. Le
     * clerc choisit alors une autre recette au lieu de fabriquer un lot qui
     * disparaîtrait derrière une offre déjà présente.
     */
    public static boolean hasAvailableProducedOffer(Villager merchant,
                                                     ItemStack produced) {
        if (merchant == null || produced == null || produced.isEmpty()) {
            return false;
        }
        for (MerchantOffer offer : merchant.getOffers()) {
            ItemStack result = offer.getResult();
            if (!offer.isOutOfStock()
                    && ItemStack.isSameItemSameComponents(result, produced)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsExactResult(MerchantOffers offers,
                                               ItemStack candidate) {
        if (offers == null || candidate == null || candidate.isEmpty()) {
            return false;
        }
        for (MerchantOffer offer : offers) {
            ItemStack result = offer.getResult();
            if (ItemStack.isSameItemSameComponents(result, candidate)) {
                return true;
            }
        }
        return false;
    }

    public static boolean accepts(VillagerProfession profession,
                                  ItemStack stack) {
        if (profession == null || stack == null || stack.isEmpty()) {
            return false;
        }
        String path = itemPath(stack);
        if (ProfessionRecipeCatalog.isIngredient(profession, stack)) {
            return true;
        }
        if (profession == VillagerProfession.ARMORER
                || profession == VillagerProfession.TOOLSMITH
                || profession == VillagerProfession.WEAPONSMITH) {
            return isEquipmentOrTool(stack, path)
                    || ProfessionStockController.isSmithMaterial(stack);
        }
        if (profession == VillagerProfession.FARMER) {
            return isFood(stack) || isCultivation(path)
                    || stack.is(Items.EGG) || stack.is(Items.TURTLE_EGG);
        }
        if (profession == VillagerProfession.FISHERMAN) {
            return isVanillaFishingLoot(stack);
        }
        if (profession == VillagerProfession.CLERIC) {
            return isClericItem(stack, path)
                    || ClericBrewingController.isWorkStock(stack);
        }
        if (profession == VillagerProfession.LIBRARIAN) {
            return isLibrarianItem(stack, path);
        }
        if (profession == VillagerProfession.CARTOGRAPHER) {
            return path.contains("map") || path.contains("compass")
                    || stack.is(Items.PAPER);
        }
        if (profession == VillagerProfession.BUTCHER) {
            return isFood(stack) || path.contains("leather");
        }
        if (profession == VillagerProfession.FLETCHER) {
            return path.contains("bow") || path.contains("arrow")
                    || stack.is(Items.FLINT) || stack.is(Items.FEATHER)
                    || stack.is(Items.STICK) || stack.is(Items.STRING)
                    || stack.is(Items.TRIPWIRE_HOOK);
        }
        if (profession == VillagerProfession.LEATHERWORKER) {
            return path.contains("leather") || stack.is(Items.SADDLE);
        }
        if (profession == VillagerProfession.MASON) {
            return stack.is(Items.IRON_BLOCK) || stack.is(Items.IRON_INGOT)
                    || containsAny(path, "stone", "brick", "clay",
                    "terracotta", "quartz", "granite", "diorite",
                    "andesite", "tuff");
        }
        if (profession == VillagerProfession.SHEPHERD) {
            return containsAny(path, "wool", "carpet", "dye", "banner")
                    || stack.is(Items.SHEARS);
        }
        return false;
    }

    public static boolean usefulToWanderingTrader(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return !stack.is(Items.AIR) && !stack.is(Items.BARRIER)
                && !stack.is(Items.STRUCTURE_VOID)
                && !stack.is(Items.DEBUG_STICK)
                && !stack.is(Items.COMMAND_BLOCK)
                && !stack.is(Items.CHAIN_COMMAND_BLOCK)
                && !stack.is(Items.REPEATING_COMMAND_BLOCK)
                && !stack.is(Items.STRUCTURE_BLOCK)
                && !stack.is(Items.JIGSAW);
    }

    public static boolean isVanillaFishingLoot(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (
                stack.is(Items.COD) || stack.is(Items.SALMON)
                        || stack.is(Items.TROPICAL_FISH)
                        || stack.is(Items.PUFFERFISH)
                        || stack.is(Items.BOW)
                        || stack.is(Items.ENCHANTED_BOOK)
                        || stack.is(Items.FISHING_ROD)
                        || stack.is(Items.NAME_TAG)
                        || stack.is(Items.NAUTILUS_SHELL)
                        || stack.is(Items.SADDLE)
                        || stack.is(Items.LILY_PAD)
                        || stack.is(Items.BOWL)
                        || stack.is(Items.LEATHER)
                        || stack.is(Items.LEATHER_BOOTS)
                        || stack.is(Items.ROTTEN_FLESH)
                        || stack.is(Items.STICK)
                        || stack.is(Items.STRING)
                        || stack.is(Items.POTION)
                        || stack.is(Items.BONE)
                        || stack.is(Items.INK_SAC)
                        || stack.is(Items.TRIPWIRE_HOOK));
    }


    private static int pricedFor(AbstractVillager merchant, ItemStack stack,
                                 long offerSalt, int workmanship) {
        int base = emeraldPrice(merchant == null ? null : merchant.getUUID(),
                stack, offerSalt, workmanship);
        if (merchant != null && merchant.level() instanceof ServerLevel level) {
            return VillageMarketController.quote(level,
                    merchant.blockPosition(), stack, base);
        }
        return base;
    }

    public static int emeraldPrice(UUID merchantId, ItemStack stack) {
        return emeraldPrice(merchantId, stack, 0L, 0);
    }

    public static int emeraldPrice(UUID merchantId, ItemStack stack,
                                   long offerSalt, int workmanship) {
        return DynamicItemValueController.emeraldPrice(merchantId, stack,
                offerSalt, workmanship);
    }

    /** Valeur de base avant marge, quantité, rareté et travail. */
    public static double baseUnitValue(ItemStack stack) {
        return DynamicItemValueController.unitValue(stack);
    }

    private static double rarityValue(ItemStack stack) {
        return switch (stack.getRarity()) {
            case COMMON -> 0.0D;
            case UNCOMMON -> 1.0D;
            case RARE -> 3.5D;
            case EPIC -> 8.0D;
        };
    }

    public static double deterministicMarkup(UUID merchantId,
                                              ItemStack stack) {
        return deterministicMarkup(merchantId, stack, 0L);
    }

    public static double deterministicMarkup(UUID merchantId,
                                              ItemStack stack,
                                              long offerSalt) {
        int seed = (merchantId == null ? 0 : merchantId.hashCode());
        seed = seed * 31 + itemPath(stack).hashCode();
        seed = seed * 31 + Long.hashCode(offerSalt);
        return 1.25D + Math.floorMod(seed, 51) / 100.0D;
    }

    private static long nextOfferSalt(AbstractVillager merchant) {
        String key = "vanillainstincts_recovered_offer_sequence";
        long sequence = merchant.getPersistentData().getLong(key) + 1L;
        merchant.getPersistentData().putLong(key, sequence);
        long day = merchant.level().getDayTime() / 24_000L;
        return sequence * 31L + day;
    }

    private static boolean isEquipmentOrTool(ItemStack stack, String path) {
        return stack.isDamageableItem() && containsAny(path,
                "sword", "pickaxe", "axe", "shovel", "hoe", "helmet",
                "chestplate", "leggings", "boots", "armor", "shield",
                "bow", "crossbow", "trident", "mace", "elytra", "shears",
                "fishing_rod", "flint_and_steel", "brush");
    }

    private static boolean isFood(ItemStack stack) {
        return stack.get(DataComponents.FOOD) != null;
    }

    private static boolean isCultivation(String path) {
        return containsAny(path, "seed", "wheat", "carrot", "potato",
                "beetroot", "melon", "pumpkin", "cocoa", "sugar_cane",
                "bamboo", "cactus", "sapling", "flower", "mushroom",
                "nether_wart", "kelp", "berry");
    }

    private static boolean isClericItem(ItemStack stack, String path) {
        return stack.is(Items.WRITTEN_BOOK) || stack.is(Items.ENCHANTED_BOOK)
                || stack.is(Items.BOOK) || path.contains("potion")
                || containsAny(path, "blaze", "ender", "ghast", "magma",
                "redstone", "glowstone", "experience_bottle", "amethyst");
    }

    private static boolean isLibrarianItem(ItemStack stack, String path) {
        return !stack.is(Items.WRITTEN_BOOK)
                && isBookOrPaper(stack, path);
    }

    private static boolean isBookOrPaper(ItemStack stack, String path) {
        return stack.is(Items.BOOK) || stack.is(Items.WRITTEN_BOOK)
                || stack.is(Items.WRITABLE_BOOK)
                || stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.PAPER)
                || path.contains("bookshelf");
    }

    private static String itemPath(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                .toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}

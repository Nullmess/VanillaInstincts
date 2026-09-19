package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.LegacyRegistry;
import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.ProfessionRules;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemFood;
import net.minecraft.init.Items;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
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

    public static boolean contribute(EntityVillager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        if (villager == null || villager.isChild() || villager.isTrading()
                || state.danger(gameTime) != null
                || !scanNow(villager, gameTime)) {
            return false;
        }
        LegacyVillagerProfession profession = LegacyVillagerProfession.of(villager);
        if (profession == LegacyVillagerProfession.NONE
                || profession == LegacyVillagerProfession.NITWIT) {
            return false;
        }
        EntityItem item = nearestAccepted(villager, level, profession, false);
        if (item == null) return false;

        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, item)
                > ProfessionRules.RECOVERED_TRADE_REACH_SQR) {
            plan.offerNavigation(VanillaInstinctsState.RECOVERED_TRADE,
                    ActionOwner.VILLAGER_PROFESSION,
                    ProfessionRules.PRIORITY_RECOVERED_TRADE,
                    fr.vanillainstincts.compat.Minecraft17Compat.position(item), ProfessionRules.RECOVERED_TRADE_SPEED,
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


    private static boolean scanNow(EntityVillager merchant, long gameTime) {
        return Math.floorMod(gameTime + merchant.getEntityId() * 23L,
                ProfessionRules.RECOVERED_TRADE_SCAN_INTERVAL_TICKS) == 0L;
    }

    private static EntityItem nearestAccepted(EntityVillager merchant,
                                               WorldServer level,
                                               LegacyVillagerProfession profession,
                                               boolean wandering) {
        double radius = ProfessionRules.RECOVERED_TRADE_SCAN_RADIUS;
        return fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityItem.class,
                        fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(merchant).expand(radius, 16.0D, radius),
                        entity -> entity.isEntityAlive() && !fr.vanillainstincts.compat.Minecraft17Compat.cannotPickup(entity)
                                && !VillagerFoodExchangeController
                                .isVillageOrigin(entity)
                                && (wandering
                                ? usefulToWanderingTrader(entity.getEntityItem())
                                : accepts(profession, entity.getEntityItem())))
                .stream()
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(merchant, value))))
                .orElse(null);
    }

    public static boolean recover(EntityVillager merchant, EntityItem entity) {
        if (merchant == null || entity == null || !entity.isEntityAlive()) {
            return false;
        }
        ItemStack found = entity.getEntityItem();
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(found)) return false;
        boolean playerOrigin = isPlayerOrigin(entity);
        MerchantRecipeList offers = fr.vanillainstincts.compat.Minecraft112Compat.offers(merchant);

        ItemStack result = found.copy();
        long offerSalt = nextOfferSalt(merchant);
        int workmanship = 0;
        if (!playerOrigin && merchant instanceof EntityVillager) { EntityVillager villager = (EntityVillager) (merchant); 
            result = ProfessionStockController.reserveForWork(villager,
                    result);
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(result)) {
                SmithRepairController.RepairResult repair =
                        SmithRepairController.maybeRepair(villager, result,
                                offerSalt);
                result = repair.stack();
                workmanship = repair.workmanship();
            }
        }
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(result)) {
            merchant.swingItem();
            fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(entity);
            return true;
        }

        int maxUses = recoveredOfferMaxUses(playerOrigin);
        if (!playerOrigin && containsExactResult(offers, result)) {
            fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(entity);
            return true;
        }
        if (offers.size() >= ProfessionRules.MAX_RECOVERED_TRADE_OFFERS) {
            return false;
        }
        int price = pricedFor(merchant, result, offerSalt, workmanship);
        int incidentScore = merchant.getEntityData()
                .getInteger("vanillainstincts_price_incident_score");
        int emeraldCost = Math.max(1, Math.min(64, price + Math.max(0, incidentScore)));
        MerchantRecipe recoveredOffer = fr.vanillainstincts.compat.Minecraft17Compat.merchantRecipe(
                new ItemStack(Items.emerald, emeraldCost), null, result, 0, maxUses);
        offers.add(recoveredOffer);
        if (playerOrigin) {
            markPlayerRecoveredOffer(merchant, offers.size() - 1);
        }
        merchant.swingItem();
        fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(entity);
        return true;
    }

    /** Marque sans ambiguïté un objet créé par l'action de jeter du joueur. */
    public static void markPlayerToss(EntityItem entity, EntityPlayer player) {
        if (entity == null || player == null) return;
        markPlayerToss(entity);
        entity.setThrower(player.getUniqueID().toString());
    }

    /** Variante utilisée lorsque l'origine joueur est déjà garantie. */
    public static void markPlayerToss(EntityItem entity) {
        if (entity == null) return;
        entity.getEntityData().setBoolean(PLAYER_TOSSED_ITEM, true);
    }

    /** Marque une pile créée par la mort du joueur, sans changer son contenu. */
    public static void markPlayerDeathDrop(EntityItem entity, EntityPlayer player) {
        if (player == null) return;
        markPlayerDeathDrop(entity);
    }

    public static void markPlayerDeathDrop(EntityItem entity) {
        if (entity == null) return;
        entity.getEntityData().setBoolean(PLAYER_DEATH_DROP, true);
    }

    public static boolean isPlayerDeathDrop(EntityItem entity) {
        return entity != null && entity.getEntityData()
                .getBoolean(PLAYER_DEATH_DROP);
    }

    public static boolean isPlayerOrigin(EntityItem entity) {
        if (entity == null) return false;
        if (entity.getEntityData().getBoolean(PLAYER_TOSSED_ITEM)
                || entity.getEntityData().getBoolean(PLAYER_DEATH_DROP)) {
            return true;
        }
        // 1.12 stores EntityItem ownership as a legacy String. The explicit
        // toss/death NBT markers above are the authoritative origin signal.
        return false;
    }

    /** Compatibilité avec les anciens tests et appels. */
    public static boolean isPlayerTossed(EntityItem entity) {
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
            EntityVillager merchant, MerchantRecipe tradedOffer) {
        if (merchant == null || tradedOffer == null) return false;
        MerchantRecipeList offers = fr.vanillainstincts.compat.Minecraft112Compat.offers(merchant);
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

    private static void markPlayerRecoveredOffer(EntityVillager merchant,
                                                  int index) {
        int[] previous = merchant.getEntityData()
                .getIntArray(PLAYER_RECOVERED_OFFER_INDICES);
        int[] updated = java.util.Arrays.copyOf(previous, previous.length + 1);
        updated[previous.length] = index;
        merchant.getEntityData().setIntArray(
                PLAYER_RECOVERED_OFFER_INDICES, updated);
    }

    private static boolean isPlayerRecoveredOffer(EntityVillager merchant,
                                                   int index) {
        for (int stored : merchant.getEntityData()
                .getIntArray(PLAYER_RECOVERED_OFFER_INDICES)) {
            if (stored == index) return true;
        }
        return false;
    }

    private static void shiftPlayerRecoveredIndicesAfterRemoval(
            EntityVillager merchant, int removedIndex) {
        int[] previous = merchant.getEntityData()
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
        merchant.getEntityData().setIntArray(
                PLAYER_RECOVERED_OFFER_INDICES, updated);
    }

    /**
     * Ajoute à la marchandise un objet réellement produit par le métier.
     * Contrairement aux objets trouvés au sol, l'offre possède un stock borné
     * afin que la production visible serve réellement de réapprovisionnement.
     */
    public static boolean addProducedOffer(EntityVillager merchant,
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
    public static boolean addProducedOffer(EntityVillager merchant,
                                           ItemStack produced, int uses) {
        return addProducedOffer(merchant, produced, uses, 1);
    }

    public static boolean addProducedOffer(EntityVillager merchant,
                                           ItemStack produced, int uses,
                                           int workmanship) {
        if (merchant == null || produced == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(produced)
                || uses <= 0) {
            return false;
        }
        MerchantRecipeList offers = fr.vanillainstincts.compat.Minecraft112Compat.offers(merchant);
        for (int index = 0; index < offers.size(); index++) {
            MerchantRecipe existing = (MerchantRecipe) offers.get(index);
            ItemStack result = existing.getItemToSell();
            if (fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(result, produced)) {
                // Une offre encore disponible représente déjà un stock réel.
                // Le sélecteur de recettes évite normalement ce cas ; on ne
                // fait surtout pas disparaître un nouveau lot derrière elle.
                if (!existing.isRecipeDisabled()) return false;
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
        int incidentScore = merchant.getEntityData()
                .getInteger("vanillainstincts_price_incident_score");
        int emeraldCost = Math.max(1, Math.min(64, price + Math.max(0, incidentScore)));
        MerchantRecipe offer = fr.vanillainstincts.compat.Minecraft17Compat.merchantRecipe(
                new ItemStack(Items.emerald, emeraldCost), null, result, 0, uses);
        offers.add(offer);
        return true;
    }

    /**
     * Indique qu'un produit identique possède encore du stock marchand. Le
     * clerc choisit alors une autre recette au lieu de fabriquer un lot qui
     * disparaîtrait derrière une offre déjà présente.
     */
    public static boolean hasAvailableProducedOffer(EntityVillager merchant,
                                                     ItemStack produced) {
        if (merchant == null || produced == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(produced)) {
            return false;
        }
        for (MerchantRecipe offer : fr.vanillainstincts.compat.Minecraft112Compat.offerList(merchant)) {
            ItemStack result = offer.getItemToSell();
            if (!offer.isRecipeDisabled()
                    && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(result, produced)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsExactResult(MerchantRecipeList offers,
                                               ItemStack candidate) {
        if (offers == null || candidate == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(candidate)) {
            return false;
        }
        for (Object rawOffer : offers) {
            if (!(rawOffer instanceof MerchantRecipe)) continue;
            MerchantRecipe offer = (MerchantRecipe) rawOffer;
            ItemStack result = offer.getItemToSell();
            if (fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(result, candidate)) {
                return true;
            }
        }
        return false;
    }

    public static boolean accepts(LegacyVillagerProfession profession,
                                  ItemStack stack) {
        if (profession == null || stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) {
            return false;
        }
        String path = itemPath(stack);
        if (ProfessionRecipeCatalog.isIngredient(profession, stack)) {
            return true;
        }
        if (profession == LegacyVillagerProfession.ARMORER
                || profession == LegacyVillagerProfession.TOOLSMITH
                || profession == LegacyVillagerProfession.WEAPONSMITH) {
            return isEquipmentOrTool(stack, path)
                    || ProfessionStockController.isSmithMaterial(stack);
        }
        if (profession == LegacyVillagerProfession.FARMER) {
            return isFood(stack) || isCultivation(path)
                    || stack.getItem().equals(Items.egg);
        }
        if (profession == LegacyVillagerProfession.FISHERMAN) {
            return isVanillaFishingLoot(stack);
        }
        if (profession == LegacyVillagerProfession.CLERIC) {
            return isClericItem(stack, path)
                    || ClericBrewingController.isWorkStock(stack);
        }
        if (profession == LegacyVillagerProfession.LIBRARIAN) {
            return isLibrarianItem(stack, path);
        }
        if (profession == LegacyVillagerProfession.CARTOGRAPHER) {
            return path.contains("map") || path.contains("compass")
                    || stack.getItem().equals(Items.paper);
        }
        if (profession == LegacyVillagerProfession.BUTCHER) {
            return isFood(stack) || path.contains("leather");
        }
        if (profession == LegacyVillagerProfession.FLETCHER) {
            return path.contains("bow") || path.contains("arrow")
                    || stack.getItem().equals(Items.flint) || stack.getItem().equals(Items.feather)
                    || stack.getItem().equals(Items.stick) || stack.getItem().equals(Items.string)
                    || stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.tripwire_hook));
        }
        if (profession == LegacyVillagerProfession.LEATHERWORKER) {
            return path.contains("leather") || stack.getItem().equals(Items.saddle);
        }
        if (profession == LegacyVillagerProfession.MASON) {
            return stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.iron_block)) || stack.getItem().equals(Items.iron_ingot)
                    || containsAny(path, "stone", "brick", "clay",
                    "terracotta", "quartz", "granite", "diorite",
                    "andesite", "tuff");
        }
        if (profession == LegacyVillagerProfession.SHEPHERD) {
            return containsAny(path, "wool", "carpet", "dye", "banner")
                    || stack.getItem().equals(Items.shears);
        }
        return false;
    }

    public static boolean usefulToWanderingTrader(ItemStack stack) {
        if (stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return false;
        return !stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.air))
                && !fr.vanillainstincts.compat.Minecraft110Compat.isBlockItem(stack, "structure_void")
                && !stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.command_block));
    }

    public static boolean isVanillaFishingLoot(ItemStack stack) {
        return stack != null && !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack) && (
                stack.getItem().equals(Items.fish) || stack.getItem().equals(Items.fish)
                        || stack.getItem().equals(Items.fish)
                        || stack.getItem().equals(Items.fish)
                        || stack.getItem().equals(Items.bow)
                        || stack.getItem().equals(Items.enchanted_book)
                        || stack.getItem().equals(Items.fishing_rod)
                        || stack.getItem().equals(Items.name_tag)
                        || stack.getItem().equals(Items.saddle)
                        || stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.waterlily))
                        || stack.getItem().equals(Items.bowl)
                        || stack.getItem().equals(Items.leather)
                        || stack.getItem().equals(Items.leather_boots)
                        || stack.getItem().equals(Items.rotten_flesh)
                        || stack.getItem().equals(Items.stick)
                        || stack.getItem().equals(Items.string)
                        || stack.getItem().equals(Items.potionitem)
                        || stack.getItem().equals(Items.bone)
                        || stack.getItem().equals(Items.dye)
                        || stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.tripwire_hook)));
    }


    private static int pricedFor(EntityVillager merchant, ItemStack stack,
                                 long offerSalt, int workmanship) {
        int base = emeraldPrice(merchant == null ? null : merchant.getUniqueID(),
                stack, offerSalt, workmanship);
        if (merchant != null && merchant.worldObj instanceof WorldServer) { WorldServer level = (WorldServer) (merchant.worldObj); 
            return VillageMarketController.quote(level,
                    entityBlockPos(merchant), stack, base);
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
        String rarity = String.valueOf(stack.getRarity()).toLowerCase(java.util.Locale.ROOT);
        if (rarity.contains("epic")) return 8.0D;
        if (rarity.contains("rare")) return 3.5D;
        if (rarity.contains("uncommon")) return 1.0D;
        return 0.0D;
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

    private static long nextOfferSalt(EntityVillager merchant) {
        String key = "vanillainstincts_recovered_offer_sequence";
        long sequence = merchant.getEntityData().getLong(key) + 1L;
        merchant.getEntityData().setLong(key, sequence);
        long day = merchant.worldObj.getWorldTime() / 24_000L;
        return sequence * 31L + day;
    }

    private static boolean isEquipmentOrTool(ItemStack stack, String path) {
        return stack.isItemStackDamageable() && containsAny(path,
                "sword", "pickaxe", "axe", "shovel", "hoe", "helmet",
                "chestplate", "leggings", "boots", "armor", "shield",
                "bow", "crossbow", "trident", "mace", "elytra", "shears",
                "fishing_rod", "flint_and_steel", "brush");
    }

    private static boolean isFood(ItemStack stack) {
        return stack.getItem() instanceof ItemFood;
    }

    private static boolean isCultivation(String path) {
        return containsAny(path, "seed", "wheat", "carrot", "potato",
                "beetroot", "melon", "pumpkin", "cocoa", "sugar_cane",
                "bamboo", "cactus", "sapling", "flower", "mushroom",
                "nether_wart", "kelp", "berry");
    }

    private static boolean isClericItem(ItemStack stack, String path) {
        return stack.getItem().equals(Items.written_book) || stack.getItem().equals(Items.enchanted_book)
                || stack.getItem().equals(Items.book) || path.contains("potion")
                || containsAny(path, "blaze", "ender", "ghast", "magma",
                "redstone", "glowstone", "experience_bottle", "amethyst");
    }

    private static boolean isLibrarianItem(ItemStack stack, String path) {
        return !stack.getItem().equals(Items.written_book)
                && isBookOrPaper(stack, path);
    }

    private static boolean isBookOrPaper(ItemStack stack, String path) {
        return stack.getItem().equals(Items.book) || stack.getItem().equals(Items.written_book)
                || stack.getItem().equals(Items.writable_book)
                || stack.getItem().equals(Items.enchanted_book) || stack.getItem().equals(Items.paper)
                || path.contains("bookshelf");
    }

    private static String itemPath(ItemStack stack) {
        if (stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return "";
        return fr.vanillainstincts.compat.LegacyResourceLocation.path(LegacyRegistry.ITEM.getKey(stack.getItem()))
                .toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}

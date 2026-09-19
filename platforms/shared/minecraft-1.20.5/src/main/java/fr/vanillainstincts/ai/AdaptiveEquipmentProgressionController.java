package fr.vanillainstincts.ai;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.DifficultyTier;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.AdaptiveEquipmentRules;
import fr.vanillainstincts.core.rules.AdaptiveEquipmentRules.Tier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Rare equipment tiers unlocked by the nearby player's vanilla progression.
 * The player demonstrates mastery first; only then can natural hostile spawns
 * occasionally mirror that equipment tier.
 */
public final class AdaptiveEquipmentProgressionController {
    private static final ResourceLocation SUIT_UP = advancement(
            "story/obtain_armor");
    private static final ResourceLocation COVER_ME_WITH_DIAMONDS = advancement(
            "story/shiny_gear");
    private static final ResourceLocation COVER_ME_WITH_DEBRIS = advancement(
            "nether/netherite_armor");
    private static final ResourceLocation ENCHANTER = advancement(
            "story/enchant_item");

    private static final String TARGET =
            "vanillainstincts_equipment_progression_target";
    private static final String EQUIPMENT_TIER =
            "vanillainstincts_equipment_progression_tier";
    private static final String ENCHANTED =
            "vanillainstincts_equipment_progression_enchanted";
    private static final String ENCHANT_LEVEL =
            "vanillainstincts_equipment_progression_enchant_level";

    private AdaptiveEquipmentProgressionController() {
    }

    public static void onMobJoin(Mob mob, ServerLevel level,
                                 boolean loadedFromDisk) {
        if (!eligible(mob) || level == null) return;
        if (tier(mob) != null || loadedFromDisk
                || mob.getSpawnType() != MobSpawnType.NATURAL) {
            return;
        }

        ServerPlayer owner = nearestProgressedPlayer(mob, level);
        if (owner == null) return;
        DifficultyTier difficulty = FeatureGate.difficulty(level);
        Tier selected = selectTier(mob, owner, level, difficulty);
        if (selected == null) return;

        boolean enchanted = hasAdvancement(owner, ENCHANTER)
                && mob.getRandom().nextDouble() < enchantedChance(difficulty);
        promote(mob, owner.getUUID(), selected, enchanted, difficulty, level);
    }

    public static Tier tier(Mob mob) {
        if (mob == null) return null;
        return Tier.fromId(mob.getPersistentData().getInt(EQUIPMENT_TIER));
    }

    public static boolean isEnchantedVariant(Mob mob) {
        return mob != null && mob.getPersistentData().getBoolean(ENCHANTED);
    }

    /** Tier-aware axe used by shield-counter variants when entering melee. */
    public static ItemStack adaptiveAxe(Mob mob) {
        Tier tier = tier(mob);
        Item item = switch (tier == null ? Tier.IRON : tier) {
            case IRON -> Items.IRON_AXE;
            case DIAMOND -> Items.DIAMOND_AXE;
            case NETHERITE -> Items.NETHERITE_AXE;
        };
        return prepareWeapon(mob, new ItemStack(item));
    }

    /** Tier-aware bow enchantments while preserving the skeleton bow identity. */
    public static ItemStack adaptiveBow(Mob mob) {
        return prepareWeapon(mob, new ItemStack(Items.BOW));
    }

    public static ItemStack prepareWeapon(Mob mob, ItemStack stack) {
        if (mob == null || stack == null || stack.isEmpty()) return stack;
        if (!(mob.level() instanceof ServerLevel level)
                || !isEnchantedVariant(mob)) {
            return stack;
        }
        int enchantLevel = enchantmentLevel(mob);
        if (stack.is(Items.BOW)) {
            addEnchantment(level, stack, Enchantments.POWER, enchantLevel);
        } else if (isSwordOrAxe(stack)) {
            addEnchantment(level, stack, Enchantments.SHARPNESS,
                    enchantLevel);
        }
        return stack;
    }

    public static ResourceLocation suitUpAdvancementId() {
        return SUIT_UP;
    }

    public static ResourceLocation diamondAdvancementId() {
        return COVER_ME_WITH_DIAMONDS;
    }

    public static ResourceLocation netheriteAdvancementId() {
        return COVER_ME_WITH_DEBRIS;
    }

    public static ResourceLocation enchanterAdvancementId() {
        return ENCHANTER;
    }

    private static Tier selectTier(Mob mob, ServerPlayer owner,
                                   ServerLevel level,
                                   DifficultyTier difficulty) {
        if (hasAdvancement(owner, COVER_ME_WITH_DEBRIS)
                && canPromote(mob, level, Tier.NETHERITE, difficulty)) {
            return Tier.NETHERITE;
        }
        if (hasAdvancement(owner, COVER_ME_WITH_DIAMONDS)
                && canPromote(mob, level, Tier.DIAMOND, difficulty)) {
            return Tier.DIAMOND;
        }
        if (hasAdvancement(owner, SUIT_UP)
                && canPromote(mob, level, Tier.IRON, difficulty)) {
            return Tier.IRON;
        }
        return null;
    }

    private static boolean canPromote(Mob mob, ServerLevel level, Tier tier,
                                      DifficultyTier difficulty) {
        if (countLocal(level, mob, tier)
                >= AdaptiveEquipmentRules.localLimit(tier)) {
            return false;
        }
        double normalChance = RuntimeConfig.chance(
                FeatureFlag.ADAPTIVE_EQUIPMENT, tier.normalChance());
        double chance = AdaptiveEquipmentRules.spawnChance(
                normalChance, difficulty);
        return mob.getRandom().nextDouble() < chance;
    }

    private static double enchantedChance(DifficultyTier difficulty) {
        double normalChance = RuntimeConfig.chance(
                FeatureFlag.ADAPTIVE_EQUIPMENT,
                AdaptiveEquipmentRules.ENCHANTED_NORMAL_CHANCE);
        return AdaptiveEquipmentRules.spawnChance(normalChance, difficulty);
    }

    private static void promote(Mob mob, UUID targetId, Tier tier,
                                boolean enchanted,
                                DifficultyTier difficulty,
                                ServerLevel level) {
        CompoundTag data = mob.getPersistentData();
        data.putUUID(TARGET, targetId);
        data.putInt(EQUIPMENT_TIER, tier.id());
        data.putBoolean(ENCHANTED, enchanted);
        data.putInt(ENCHANT_LEVEL,
                AdaptiveEquipmentRules.enchantmentLevel(tier, difficulty));

        int pieces = armorPieceCount(mob, tier, difficulty);
        equipArmor(mob, level, tier, pieces, enchanted);
        equipRoleWeapon(mob, tier);
    }

    private static int armorPieceCount(Mob mob, Tier tier,
                                       DifficultyTier difficulty) {
        int maximum = AdaptiveEquipmentRules.maximumArmorPieces(
                tier, difficulty);
        if (maximum <= 0) return 0;
        if (tier == Tier.NETHERITE && difficulty == DifficultyTier.HARD
                && mob.getRandom().nextDouble()
                < AdaptiveEquipmentRules.FULL_NETHERITE_VARIANT_CHANCE) {
            return 4;
        }
        return 1 + mob.getRandom().nextInt(maximum);
    }

    private static void equipArmor(Mob mob, ServerLevel level, Tier tier,
                                   int pieces, boolean enchanted) {
        List<EquipmentSlot> slots = new ArrayList<>(List.of(
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET));
        int remaining = Math.min(pieces, slots.size());
        while (remaining-- > 0 && !slots.isEmpty()) {
            int index = mob.getRandom().nextInt(slots.size());
            EquipmentSlot slot = slots.remove(index);
            if (!shouldReplaceArmor(mob.getItemBySlot(slot), tier)) continue;
            ItemStack stack = armorFor(tier, slot);
            wearEquipment(stack, tier, mob.getRandom());
            if (enchanted) enchantArmor(level, stack, slot,
                    enchantmentLevel(mob));
            mob.setItemSlot(slot, stack);
        }
    }

    private static void equipRoleWeapon(Mob mob, Tier tier) {
        if (mob instanceof AbstractSkeleton) {
            if (mob.getMainHandItem().is(Items.BOW)) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, adaptiveBow(mob));
            }
            return;
        }
        if (!(mob instanceof Zombie zombie)) return;
        if (AdaptiveProgressionController.isAngler(zombie)
                || AdaptiveProgressionController.isPearlHunter(zombie)) {
            return;
        }
        if (AdaptiveShieldResponseController.isZombieFlanker(zombie)) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, adaptiveAxe(mob));
            return;
        }
        ItemStack current = mob.getMainHandItem();
        if (!current.isEmpty() && !isSwordOrAxe(current)) return;
        ItemStack weapon = mob.getRandom().nextBoolean()
                ? swordFor(tier) : adaptiveAxe(mob);
        if (!weapon.is(Items.BOW)) {
            wearEquipment(weapon, tier, mob.getRandom());
        }
        mob.setItemSlot(EquipmentSlot.MAINHAND,
                prepareWeapon(mob, weapon));
    }

    private static ItemStack swordFor(Tier tier) {
        Item item = switch (tier) {
            case IRON -> Items.IRON_SWORD;
            case DIAMOND -> Items.DIAMOND_SWORD;
            case NETHERITE -> Items.NETHERITE_SWORD;
        };
        return new ItemStack(item);
    }

    private static ItemStack armorFor(Tier tier, EquipmentSlot slot) {
        Item item = switch (tier) {
            case IRON -> switch (slot) {
                case HEAD -> Items.IRON_HELMET;
                case CHEST -> Items.IRON_CHESTPLATE;
                case LEGS -> Items.IRON_LEGGINGS;
                case FEET -> Items.IRON_BOOTS;
                default -> Items.AIR;
            };
            case DIAMOND -> switch (slot) {
                case HEAD -> Items.DIAMOND_HELMET;
                case CHEST -> Items.DIAMOND_CHESTPLATE;
                case LEGS -> Items.DIAMOND_LEGGINGS;
                case FEET -> Items.DIAMOND_BOOTS;
                default -> Items.AIR;
            };
            case NETHERITE -> switch (slot) {
                case HEAD -> Items.NETHERITE_HELMET;
                case CHEST -> Items.NETHERITE_CHESTPLATE;
                case LEGS -> Items.NETHERITE_LEGGINGS;
                case FEET -> Items.NETHERITE_BOOTS;
                default -> Items.AIR;
            };
        };
        return new ItemStack(item);
    }

    private static void enchantArmor(ServerLevel level, ItemStack stack,
                                     EquipmentSlot slot, int enchantLevel) {
        Enchantment enchantment = slot == EquipmentSlot.FEET
                ? Enchantments.FEATHER_FALLING : Enchantments.PROTECTION;
        addEnchantment(level, stack, enchantment, enchantLevel);
    }

    private static void addEnchantment(ServerLevel level, ItemStack stack,
                                       Enchantment enchantment,
                                       int enchantLevel) {
        stack.enchant(enchantment, enchantLevel);
    }

    private static int enchantmentLevel(Mob mob) {
        return Math.max(1, Math.min(4,
                mob.getPersistentData().getInt(ENCHANT_LEVEL)));
    }

    private static boolean shouldReplaceArmor(ItemStack current, Tier tier) {
        return current.isEmpty() || armorRank(current) < tier.id();
    }

    private static int armorRank(ItemStack stack) {
        if (stack.is(Items.NETHERITE_HELMET)
                || stack.is(Items.NETHERITE_CHESTPLATE)
                || stack.is(Items.NETHERITE_LEGGINGS)
                || stack.is(Items.NETHERITE_BOOTS)) return 3;
        if (stack.is(Items.DIAMOND_HELMET)
                || stack.is(Items.DIAMOND_CHESTPLATE)
                || stack.is(Items.DIAMOND_LEGGINGS)
                || stack.is(Items.DIAMOND_BOOTS)) return 2;
        if (stack.is(Items.IRON_HELMET)
                || stack.is(Items.IRON_CHESTPLATE)
                || stack.is(Items.IRON_LEGGINGS)
                || stack.is(Items.IRON_BOOTS)) return 1;
        return 0;
    }

    private static void wearEquipment(ItemStack stack, Tier tier,
                                      RandomSource random) {
        if (stack == null || !stack.isDamageableItem()) return;
        double minimum = switch (tier) {
            case IRON -> 0.25D;
            case DIAMOND -> 0.40D;
            case NETHERITE -> 0.60D;
        };
        double maximum = switch (tier) {
            case IRON -> 0.60D;
            case DIAMOND -> 0.72D;
            case NETHERITE -> 0.86D;
        };
        double ratio = minimum + random.nextDouble() * (maximum - minimum);
        int damage = (int) Math.round(stack.getMaxDamage() * ratio);
        stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, damage));
    }

    private static boolean isSwordOrAxe(ItemStack stack) {
        return stack.is(Items.IRON_SWORD) || stack.is(Items.DIAMOND_SWORD)
                || stack.is(Items.NETHERITE_SWORD)
                || stack.is(Items.IRON_AXE) || stack.is(Items.DIAMOND_AXE)
                || stack.is(Items.NETHERITE_AXE);
    }

    private static boolean eligible(Mob mob) {
        if (mob instanceof Zombie zombie) {
            return !(zombie instanceof ZombieVillager) && !zombie.isBaby();
        }
        if (mob instanceof AbstractSkeleton) {
            return !(mob instanceof WitherSkeleton);
        }
        return mob instanceof AbstractPiglin;
    }

    private static ServerPlayer nearestProgressedPlayer(Mob mob,
                                                         ServerLevel level) {
        double range = AdaptiveEquipmentRules.OWNER_SEARCH_RANGE;
        double maxDistanceSqr = range * range;
        return level.players().stream()
                .filter(AdaptiveEquipmentProgressionController::combatTarget)
                .filter(AdaptiveEquipmentProgressionController
                        ::hasAnyEquipmentAdvancement)
                .filter(player -> mob.distanceToSqr(player) <= maxDistanceSqr)
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }

    private static boolean hasAnyEquipmentAdvancement(ServerPlayer player) {
        return hasAdvancement(player, SUIT_UP)
                || hasAdvancement(player, COVER_ME_WITH_DIAMONDS)
                || hasAdvancement(player, COVER_ME_WITH_DEBRIS);
    }

    private static boolean hasAdvancement(ServerPlayer player,
                                          ResourceLocation id) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null) return false;
        AdvancementHolder advancement = server.getAdvancements().get(id);
        return advancement != null && player.getAdvancements()
                .getOrStartProgress(advancement).isDone();
    }

    private static boolean combatTarget(ServerPlayer player) {
        return player != null && player.isAlive()
                && !player.isCreative() && !player.isSpectator();
    }

    private static int countLocal(ServerLevel level, Mob origin, Tier tier) {
        double radius = AdaptiveEquipmentRules.LOCAL_VARIANT_RADIUS;
        return level.getEntitiesOfClass(Mob.class,
                origin.getBoundingBox().inflate(radius), candidate ->
                        tier(candidate) == tier).size();
    }

    private static ResourceLocation advancement(String path) {
        return new ResourceLocation("minecraft", path);
    }
}

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
import net.minecraft.advancements.Advancement;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import java.util.Random;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.AbstractSkeletonEntity;
import net.minecraft.entity.monster.WitherSkeletonEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.monster.ZombieVillagerEntity;
import net.minecraft.entity.monster.piglin.PiglinEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;

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

    public static void onMobJoin(MobEntity mob, ServerWorld level,
                                 boolean loadedFromDisk) {
        if (!eligible(mob) || level == null) return;
        if (tier(mob) != null || loadedFromDisk
               ) {
            return;
        }

        ServerPlayerEntity owner = nearestProgressedPlayer(mob, level);
        if (owner == null) return;
        DifficultyTier difficulty = FeatureGate.difficulty(level);
        Tier selected = selectTier(mob, owner, level, difficulty);
        if (selected == null) return;

        boolean enchanted = hasAdvancement(owner, ENCHANTER)
                && mob.getRandom().nextDouble() < enchantedChance(difficulty);
        promote(mob, owner.getUUID(), selected, enchanted, difficulty, level);
    }

    public static Tier tier(MobEntity mob) {
        if (mob == null) return null;
        return Tier.fromId(mob.getPersistentData().getInt(EQUIPMENT_TIER));
    }

    public static boolean isEnchantedVariant(MobEntity mob) {
        return mob != null && mob.getPersistentData().getBoolean(ENCHANTED);
    }

    /** Tier-aware axe used by shield-counter variants when entering melee. */
    public static ItemStack adaptiveAxe(MobEntity mob) {
        Tier tier = tier(mob);
        Item item = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((tier == null ? Tier.IRON : tier)) { case IRON:  return Items.IRON_AXE; case DIAMOND:  return Items.DIAMOND_AXE; case NETHERITE:  return Items.NETHERITE_AXE;  default: throw new AssertionError("Unexpected switch value"); } });
        return prepareWeapon(mob, new ItemStack(item));
    }

    /** Tier-aware bow enchantments while preserving the skeleton bow identity. */
    public static ItemStack adaptiveBow(MobEntity mob) {
        return prepareWeapon(mob, new ItemStack(Items.BOW));
    }

    public static ItemStack prepareWeapon(MobEntity mob, ItemStack stack) {
        if (mob == null || stack == null || stack.isEmpty()) return stack;
        if (!(mob.level instanceof ServerWorld)
                || !isEnchantedVariant(mob)) {
            return stack;
        } ServerWorld level = (ServerWorld) (mob.level);
        int enchantLevel = enchantmentLevel(mob);
        if (stack.getItem().equals(Items.BOW)) {
            addEnchantment(level, stack, Enchantments.POWER_ARROWS, enchantLevel);
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

    private static Tier selectTier(MobEntity mob, ServerPlayerEntity owner,
                                   ServerWorld level,
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

    private static boolean canPromote(MobEntity mob, ServerWorld level, Tier tier,
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

    private static void promote(MobEntity mob, UUID targetId, Tier tier,
                                boolean enchanted,
                                DifficultyTier difficulty,
                                ServerWorld level) {
        CompoundNBT data = mob.getPersistentData();
        data.putUUID(TARGET, targetId);
        data.putInt(EQUIPMENT_TIER, tier.id());
        data.putBoolean(ENCHANTED, enchanted);
        data.putInt(ENCHANT_LEVEL,
                AdaptiveEquipmentRules.enchantmentLevel(tier, difficulty));

        int pieces = armorPieceCount(mob, tier, difficulty);
        equipArmor(mob, level, tier, pieces, enchanted);
        equipRoleWeapon(mob, tier);
    }

    private static int armorPieceCount(MobEntity mob, Tier tier,
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

    private static void equipArmor(MobEntity mob, ServerWorld level, Tier tier,
                                   int pieces, boolean enchanted) {
        List<EquipmentSlotType> slots = new ArrayList<>(fr.vanillainstincts.compat.LegacyJava8.listOf(
                EquipmentSlotType.HEAD, EquipmentSlotType.CHEST,
                EquipmentSlotType.LEGS, EquipmentSlotType.FEET));
        int remaining = Math.min(pieces, slots.size());
        while (remaining-- > 0 && !slots.isEmpty()) {
            int index = mob.getRandom().nextInt(slots.size());
            EquipmentSlotType slot = slots.remove(index);
            if (!shouldReplaceArmor(mob.getItemBySlot(slot), tier)) continue;
            ItemStack stack = armorFor(tier, slot);
            wearEquipment(stack, tier, mob.getRandom());
            if (enchanted) enchantArmor(level, stack, slot,
                    enchantmentLevel(mob));
            mob.setItemSlot(slot, stack);
        }
    }

    private static void equipRoleWeapon(MobEntity mob, Tier tier) {
        if (mob instanceof AbstractSkeletonEntity) {
            if (mob.getMainHandItem().getItem().equals(Items.BOW)) {
                mob.setItemSlot(EquipmentSlotType.MAINHAND, adaptiveBow(mob));
            }
            return;
        }
        if (!(mob instanceof ZombieEntity)) return; ZombieEntity zombie = (ZombieEntity) (mob);
        if (AdaptiveProgressionController.isAngler(zombie)
                || AdaptiveProgressionController.isPearlHunter(zombie)) {
            return;
        }
        if (AdaptiveShieldResponseController.isZombieFlanker(zombie)) {
            mob.setItemSlot(EquipmentSlotType.MAINHAND, adaptiveAxe(mob));
            return;
        }
        ItemStack current = mob.getMainHandItem();
        if (!current.isEmpty() && !isSwordOrAxe(current)) return;
        ItemStack weapon = mob.getRandom().nextBoolean()
                ? swordFor(tier) : adaptiveAxe(mob);
        if (!weapon.getItem().equals(Items.BOW)) {
            wearEquipment(weapon, tier, mob.getRandom());
        }
        mob.setItemSlot(EquipmentSlotType.MAINHAND,
                prepareWeapon(mob, weapon));
    }

    private static ItemStack swordFor(Tier tier) {
        Item item = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((tier)) { case IRON:  return Items.IRON_SWORD; case DIAMOND:  return Items.DIAMOND_SWORD; case NETHERITE:  return Items.NETHERITE_SWORD;  default: throw new AssertionError("Unexpected switch value"); } });
        return new ItemStack(item);
    }

    private static ItemStack armorFor(Tier tier, EquipmentSlotType slot) {
        Item item = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((tier)) { case IRON:  return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((slot)) { case HEAD:  return Items.IRON_HELMET; case CHEST:  return Items.IRON_CHESTPLATE; case LEGS:  return Items.IRON_LEGGINGS; case FEET:  return Items.IRON_BOOTS; default:  return Items.AIR; } }); case DIAMOND:  return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((slot)) { case HEAD:  return Items.DIAMOND_HELMET; case CHEST:  return Items.DIAMOND_CHESTPLATE; case LEGS:  return Items.DIAMOND_LEGGINGS; case FEET:  return Items.DIAMOND_BOOTS; default:  return Items.AIR; } }); case NETHERITE:  return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((slot)) { case HEAD:  return Items.NETHERITE_HELMET; case CHEST:  return Items.NETHERITE_CHESTPLATE; case LEGS:  return Items.NETHERITE_LEGGINGS; case FEET:  return Items.NETHERITE_BOOTS; default:  return Items.AIR; } });  default: throw new AssertionError("Unexpected switch value"); } });
        return new ItemStack(item);
    }

    private static void enchantArmor(ServerWorld level, ItemStack stack,
                                     EquipmentSlotType slot, int enchantLevel) {
        Enchantment enchantment = slot == EquipmentSlotType.FEET
                ? Enchantments.FALL_PROTECTION : Enchantments.ALL_DAMAGE_PROTECTION;
        addEnchantment(level, stack, enchantment, enchantLevel);
    }

    private static void addEnchantment(ServerWorld level, ItemStack stack,
                                       Enchantment enchantment,
                                       int enchantLevel) {
        stack.enchant(enchantment, enchantLevel);
    }

    private static int enchantmentLevel(MobEntity mob) {
        return Math.max(1, Math.min(4,
                mob.getPersistentData().getInt(ENCHANT_LEVEL)));
    }

    private static boolean shouldReplaceArmor(ItemStack current, Tier tier) {
        return current.isEmpty() || armorRank(current) < tier.id();
    }

    private static int armorRank(ItemStack stack) {
        if (stack.getItem().equals(Items.NETHERITE_HELMET)
                || stack.getItem().equals(Items.NETHERITE_CHESTPLATE)
                || stack.getItem().equals(Items.NETHERITE_LEGGINGS)
                || stack.getItem().equals(Items.NETHERITE_BOOTS)) return 3;
        if (stack.getItem().equals(Items.DIAMOND_HELMET)
                || stack.getItem().equals(Items.DIAMOND_CHESTPLATE)
                || stack.getItem().equals(Items.DIAMOND_LEGGINGS)
                || stack.getItem().equals(Items.DIAMOND_BOOTS)) return 2;
        if (stack.getItem().equals(Items.IRON_HELMET)
                || stack.getItem().equals(Items.IRON_CHESTPLATE)
                || stack.getItem().equals(Items.IRON_LEGGINGS)
                || stack.getItem().equals(Items.IRON_BOOTS)) return 1;
        return 0;
    }

    private static void wearEquipment(ItemStack stack, Tier tier,
                                      Random random) {
        if (stack == null || !stack.isDamageableItem()) return;
        double minimum = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((tier)) { case IRON:  return 0.25D; case DIAMOND:  return 0.40D; case NETHERITE:  return 0.60D;  default: throw new AssertionError("Unexpected switch value"); } });
        double maximum = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((tier)) { case IRON:  return 0.60D; case DIAMOND:  return 0.72D; case NETHERITE:  return 0.86D;  default: throw new AssertionError("Unexpected switch value"); } });
        double ratio = minimum + random.nextDouble() * (maximum - minimum);
        int damage = (int) Math.round(stack.getMaxDamage() * ratio);
        stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, damage));
    }

    private static boolean isSwordOrAxe(ItemStack stack) {
        return stack.getItem().equals(Items.IRON_SWORD) || stack.getItem().equals(Items.DIAMOND_SWORD)
                || stack.getItem().equals(Items.NETHERITE_SWORD)
                || stack.getItem().equals(Items.IRON_AXE) || stack.getItem().equals(Items.DIAMOND_AXE)
                || stack.getItem().equals(Items.NETHERITE_AXE);
    }

    private static boolean eligible(MobEntity mob) {
        if (mob instanceof ZombieEntity) { ZombieEntity zombie = (ZombieEntity) (mob); 
            return !(zombie instanceof ZombieVillagerEntity) && !zombie.isBaby();
        }
        if (mob instanceof AbstractSkeletonEntity) {
            return !(mob instanceof WitherSkeletonEntity);
        }
        return mob instanceof PiglinEntity;
    }

    private static ServerPlayerEntity nearestProgressedPlayer(MobEntity mob,
                                                         ServerWorld level) {
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

    private static boolean hasAnyEquipmentAdvancement(ServerPlayerEntity player) {
        return hasAdvancement(player, SUIT_UP)
                || hasAdvancement(player, COVER_ME_WITH_DIAMONDS)
                || hasAdvancement(player, COVER_ME_WITH_DEBRIS);
    }

    private static boolean hasAdvancement(ServerPlayerEntity player,
                                          ResourceLocation id) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null) return false;
        Advancement advancement = server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements()
                .getOrStartProgress(advancement).isDone();
    }

    private static boolean combatTarget(ServerPlayerEntity player) {
        return player != null && player.isAlive()
                && !player.isCreative() && !player.isSpectator();
    }

    private static int countLocal(ServerWorld level, MobEntity origin, Tier tier) {
        double radius = AdaptiveEquipmentRules.LOCAL_VARIANT_RADIUS;
        return level.getEntitiesOfClass(MobEntity.class,
                origin.getBoundingBox().inflate(radius), candidate ->
                        tier(candidate) == tier).size();
    }

    private static ResourceLocation advancement(String path) {
        return new ResourceLocation("minecraft", path);
    }
}

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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import java.util.Random;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.enchantment.Enchantment;

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

    public static void onMobJoin(EntityLiving mob, WorldServer level,
                                 boolean loadedFromDisk) {
        if (!eligible(mob) || level == null) return;
        if (tier(mob) != null || loadedFromDisk
               ) {
            return;
        }

        EntityPlayerMP owner = nearestProgressedPlayer(mob, level);
        if (owner == null) return;
        DifficultyTier difficulty = FeatureGate.difficulty(level);
        Tier selected = selectTier(mob, owner, level, difficulty);
        if (selected == null) return;

        boolean enchanted = hasAdvancement(owner, ENCHANTER)
                &&fr.vanillainstincts.compat.Minecraft112Compat.random(mob).nextDouble() < enchantedChance(difficulty);
        promote(mob, owner.getUniqueID(), selected, enchanted, difficulty, level);
    }

    public static Tier tier(EntityLiving mob) {
        if (mob == null) return null;
        return Tier.fromId(mob.getEntityData().getInteger(EQUIPMENT_TIER));
    }

    public static boolean isEnchantedVariant(EntityLiving mob) {
        return mob != null && mob.getEntityData().getBoolean(ENCHANTED);
    }

    /** Tier-aware axe used by shield-counter variants when entering melee. */
    public static ItemStack adaptiveAxe(EntityLiving mob) {
        Tier tier = tier(mob);
        Item item = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((tier == null ? Tier.IRON : tier)) { case IRON:  return Items.iron_axe; case DIAMOND:  return Items.diamond_axe; case NETHERITE:  return Items.diamond_axe;  default: throw new AssertionError("Unexpected switch value"); } });
        return prepareWeapon(mob, new ItemStack(item));
    }

    /** Tier-aware bow enchantments while preserving the skeleton bow identity. */
    public static ItemStack adaptiveBow(EntityLiving mob) {
        return prepareWeapon(mob, new ItemStack(Items.bow));
    }

    public static ItemStack prepareWeapon(EntityLiving mob, ItemStack stack) {
        if (mob == null || stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return stack;
        if (!(mob.worldObj instanceof WorldServer)
                || !isEnchantedVariant(mob)) {
            return stack;
        } WorldServer level = (WorldServer) (mob.worldObj);
        int enchantLevel = enchantmentLevel(mob);
        if (stack.getItem().equals(Items.bow)) {
            addEnchantment(level, stack, net.minecraft.enchantment.Enchantment.power, enchantLevel);
        } else if (isSwordOrAxe(stack)) {
            addEnchantment(level, stack, net.minecraft.enchantment.Enchantment.sharpness,
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

    private static Tier selectTier(EntityLiving mob, EntityPlayerMP owner,
                                   WorldServer level,
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

    private static boolean canPromote(EntityLiving mob, WorldServer level, Tier tier,
                                      DifficultyTier difficulty) {
        if (countLocal(level, mob, tier)
                >= AdaptiveEquipmentRules.localLimit(tier)) {
            return false;
        }
        double normalChance = RuntimeConfig.chance(
                FeatureFlag.ADAPTIVE_EQUIPMENT, tier.normalChance());
        double chance = AdaptiveEquipmentRules.spawnChance(
                normalChance, difficulty);
        return fr.vanillainstincts.compat.Minecraft112Compat.random(mob).nextDouble() < chance;
    }

    private static double enchantedChance(DifficultyTier difficulty) {
        double normalChance = RuntimeConfig.chance(
                FeatureFlag.ADAPTIVE_EQUIPMENT,
                AdaptiveEquipmentRules.ENCHANTED_NORMAL_CHANCE);
        return AdaptiveEquipmentRules.spawnChance(normalChance, difficulty);
    }

    private static void promote(EntityLiving mob, UUID targetId, Tier tier,
                                boolean enchanted,
                                DifficultyTier difficulty,
                                WorldServer level) {
        NBTTagCompound data = mob.getEntityData();
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(data, TARGET, targetId);
        data.setInteger(EQUIPMENT_TIER, tier.id());
        data.setBoolean(ENCHANTED, enchanted);
        data.setInteger(ENCHANT_LEVEL,
                AdaptiveEquipmentRules.enchantmentLevel(tier, difficulty));

        int pieces = armorPieceCount(mob, tier, difficulty);
        equipArmor(mob, level, tier, pieces, enchanted);
        equipRoleWeapon(mob, tier);
    }

    private static int armorPieceCount(EntityLiving mob, Tier tier,
                                       DifficultyTier difficulty) {
        int maximum = AdaptiveEquipmentRules.maximumArmorPieces(
                tier, difficulty);
        if (maximum <= 0) return 0;
        if (tier == Tier.NETHERITE && difficulty == DifficultyTier.HARD
                &&fr.vanillainstincts.compat.Minecraft112Compat.random(mob).nextDouble()
                < AdaptiveEquipmentRules.FULL_NETHERITE_VARIANT_CHANCE) {
            return 4;
        }
        return 1 +fr.vanillainstincts.compat.Minecraft112Compat.random(mob).nextInt(maximum);
    }

    private static void equipArmor(EntityLiving mob, WorldServer level, Tier tier,
                                   int pieces, boolean enchanted) {
        List<Integer> slots = new ArrayList<>(fr.vanillainstincts.compat.LegacyJava8.listOf(
                4, 3, 2, 1));
        int remaining = Math.min(pieces, slots.size());
        while (remaining-- > 0 && !slots.isEmpty()) {
            int index =fr.vanillainstincts.compat.Minecraft112Compat.random(mob).nextInt(slots.size());
            int slot = slots.remove(index);
            if (!shouldReplaceArmor(mob.getEquipmentInSlot(slot), tier)) continue;
            ItemStack stack = armorFor(tier, slot);
            wearEquipment(stack, tier,fr.vanillainstincts.compat.Minecraft112Compat.random(mob));
            if (enchanted) enchantArmor(level, stack, slot,
                    enchantmentLevel(mob));
            mob.setCurrentItemOrArmor(slot, stack);
        }
    }

    private static void equipRoleWeapon(EntityLiving mob, Tier tier) {
        if (mob instanceof EntitySkeleton) {
            if (mob.getHeldItem().getItem().equals(Items.bow)) {
                mob.setCurrentItemOrArmor(0, adaptiveBow(mob));
            }
            return;
        }
        if (!(mob instanceof EntityZombie)) return; EntityZombie zombie = (EntityZombie) (mob);
        if (AdaptiveProgressionController.isAngler(zombie)
                || AdaptiveProgressionController.isPearlHunter(zombie)) {
            return;
        }
        if (AdaptiveShieldResponseController.isZombieFlanker(zombie)) {
            mob.setCurrentItemOrArmor(0, adaptiveAxe(mob));
            return;
        }
        ItemStack current = mob.getHeldItem();
        if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(current) && !isSwordOrAxe(current)) return;
        ItemStack weapon =fr.vanillainstincts.compat.Minecraft112Compat.random(mob).nextBoolean()
                ? swordFor(tier) : adaptiveAxe(mob);
        if (!weapon.getItem().equals(Items.bow)) {
            wearEquipment(weapon, tier,fr.vanillainstincts.compat.Minecraft112Compat.random(mob));
        }
        mob.setCurrentItemOrArmor(0,
                prepareWeapon(mob, weapon));
    }

    private static ItemStack swordFor(Tier tier) {
        Item item = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((tier)) { case IRON:  return Items.iron_sword; case DIAMOND:  return Items.diamond_sword; case NETHERITE:  return Items.diamond_sword;  default: throw new AssertionError("Unexpected switch value"); } });
        return new ItemStack(item);
    }

    private static ItemStack armorFor(Tier tier, int slot) {
        boolean diamond = tier == Tier.DIAMOND || tier == Tier.NETHERITE;
        Item item;
        switch (slot) {
            case 4: item = diamond ? Items.diamond_helmet : Items.iron_helmet; break;
            case 3: item = diamond ? Items.diamond_chestplate : Items.iron_chestplate; break;
            case 2: item = diamond ? Items.diamond_leggings : Items.iron_leggings; break;
            case 1: item = diamond ? Items.diamond_boots : Items.iron_boots; break;
            default: return null;
        }
        return new ItemStack(item);
    }

    private static void enchantArmor(WorldServer level, ItemStack stack,
                                     int slot, int enchantLevel) {
        Enchantment enchantment = slot == 1
                ? net.minecraft.enchantment.Enchantment.featherFalling : net.minecraft.enchantment.Enchantment.protection;
        addEnchantment(level, stack, enchantment, enchantLevel);
    }

    private static void addEnchantment(WorldServer level, ItemStack stack,
                                       Enchantment enchantment,
                                       int enchantLevel) {
        stack.addEnchantment(enchantment, enchantLevel);
    }

    private static int enchantmentLevel(EntityLiving mob) {
        return Math.max(1, Math.min(4,
                mob.getEntityData().getInteger(ENCHANT_LEVEL)));
    }

    private static boolean shouldReplaceArmor(ItemStack current, Tier tier) {
        return fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(current) || armorRank(current) < tier.id();
    }

    private static int armorRank(ItemStack stack) {
        if (stack.getItem().equals(Items.diamond_helmet)
                || stack.getItem().equals(Items.diamond_chestplate)
                || stack.getItem().equals(Items.diamond_leggings)
                || stack.getItem().equals(Items.diamond_boots)) return 3;
        if (stack.getItem().equals(Items.diamond_helmet)
                || stack.getItem().equals(Items.diamond_chestplate)
                || stack.getItem().equals(Items.diamond_leggings)
                || stack.getItem().equals(Items.diamond_boots)) return 2;
        if (stack.getItem().equals(Items.iron_helmet)
                || stack.getItem().equals(Items.iron_chestplate)
                || stack.getItem().equals(Items.iron_leggings)
                || stack.getItem().equals(Items.iron_boots)) return 1;
        return 0;
    }

    private static void wearEquipment(ItemStack stack, Tier tier,
                                      Random random) {
        if (stack == null || !stack.isItemStackDamageable()) return;
        double minimum = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((tier)) { case IRON:  return 0.25D; case DIAMOND:  return 0.40D; case NETHERITE:  return 0.60D;  default: throw new AssertionError("Unexpected switch value"); } });
        double maximum = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((tier)) { case IRON:  return 0.60D; case DIAMOND:  return 0.72D; case NETHERITE:  return 0.86D;  default: throw new AssertionError("Unexpected switch value"); } });
        double ratio = minimum + random.nextDouble() * (maximum - minimum);
        int damage = (int) Math.round(stack.getMaxDamage() * ratio);
        stack.setItemDamage(Math.min(stack.getMaxDamage() - 1, damage));
    }

    private static boolean isSwordOrAxe(ItemStack stack) {
        return stack.getItem().equals(Items.iron_sword) || stack.getItem().equals(Items.diamond_sword)
                || stack.getItem().equals(Items.diamond_sword)
                || stack.getItem().equals(Items.iron_axe) || stack.getItem().equals(Items.diamond_axe)
                || stack.getItem().equals(Items.diamond_axe);
    }

    private static boolean eligible(EntityLiving mob) {
        if (mob instanceof EntityZombie) { EntityZombie zombie = (EntityZombie) (mob); 
            return !fr.vanillainstincts.compat.Minecraft110Compat.isZombieVillager(zombie) && !zombie.isChild();
        }
        if (mob instanceof EntitySkeleton) {
            return !((mob instanceof EntitySkeleton && fr.vanillainstincts.compat.Minecraft110Compat.isWitherSkeleton((EntitySkeleton) mob)));
        }
        return mob instanceof EntityPigZombie;
    }

    private static EntityPlayerMP nearestProgressedPlayer(EntityLiving mob,
                                                         WorldServer level) {
        double range = AdaptiveEquipmentRules.OWNER_SEARCH_RANGE;
        double maxDistanceSqr = range * range;
        return fr.vanillainstincts.compat.Minecraft112Compat.players(level).stream()
                .filter(AdaptiveEquipmentProgressionController::combatTarget)
                .filter(AdaptiveEquipmentProgressionController
                        ::hasAnyEquipmentAdvancement)
                .filter(player -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, player) <= maxDistanceSqr)
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, value))))
                .orElse(null);
    }

    private static boolean hasAnyEquipmentAdvancement(EntityPlayerMP player) {
        return hasAdvancement(player, SUIT_UP)
                || hasAdvancement(player, COVER_ME_WITH_DIAMONDS)
                || hasAdvancement(player, COVER_ME_WITH_DEBRIS);
    }

    private static boolean hasAdvancement(EntityPlayerMP player,
                                          ResourceLocation id) {
        return fr.vanillainstincts.compat.Minecraft112Compat.hasAdvancement(player, id);
    }

    private static boolean combatTarget(EntityPlayerMP player) {
        return player != null && player.isEntityAlive()
                && !player.capabilities.isCreativeMode && !player.isSpectator();
    }

    private static int countLocal(WorldServer level, EntityLiving origin, Tier tier) {
        double radius = AdaptiveEquipmentRules.LOCAL_VARIANT_RADIUS;
        return level.getEntitiesWithinAABB(EntityLiving.class,
                origin.getEntityBoundingBox().expand(radius, radius, radius), candidate ->
                        tier(candidate) == tier).size();
    }

    private static ResourceLocation advancement(String path) {
        return new ResourceLocation("minecraft", path);
    }
}

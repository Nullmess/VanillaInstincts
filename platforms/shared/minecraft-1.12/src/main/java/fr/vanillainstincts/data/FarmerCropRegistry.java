package fr.vanillainstincts.data;

import fr.vanillainstincts.compat.Minecraft115TagCompat;

import fr.vanillainstincts.compat.LegacyRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.Item;
import net.minecraft.init.Items;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.properties.IProperty;

/**
 * Farmer Compatibility 2.0 registry.
 *
 * <p>Datapacks may add files under
 * {@code data/<namespace>/farmer_crops/*.json}. A definition maps a planting
 * item to a crop block, valid ground blocks/tags and an optional integer age
 * property. Vanilla {@link BlockCrops}s remain supported automatically when no
 * explicit definition exists.</p>
 */
public final class FarmerCropRegistry {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<Item, CropDefinition> byItem = fr.vanillainstincts.compat.LegacyJava8.mapOf();
    private static volatile Map<Block, CropDefinition> byBlock = fr.vanillainstincts.compat.LegacyJava8.mapOf();

    public FarmerCropRegistry() {}

    private void apply(Map<ResourceLocation, JsonObject> objects) {
        List<CropDefinition> definitions = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonObject> entry : objects.entrySet()) {
            try {
                CropDefinition definition = parse(entry.getKey(),
                        entry.getValue());
                if (definition != null) definitions.add(definition);
            } catch (RuntimeException exception) {
                VanillaInstincts.LOGGER.warn(
                        "Définition de culture fermier ignorée ({}): {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        definitions.sort(Comparator.comparingInt(CropDefinition::priority)
                .reversed().thenComparing(def -> def.id().toString()));
        Map<Item, CropDefinition> itemIndex = new HashMap<>();
        Map<Block, CropDefinition> blockIndex = new HashMap<>();
        for (CropDefinition definition : definitions) {
            itemIndex.putIfAbsent(definition.item(), definition);
            blockIndex.putIfAbsent(definition.crop(), definition);
        }
        byItem = fr.vanillainstincts.compat.LegacyJava8.copyMap(itemIndex);
        byBlock = fr.vanillainstincts.compat.LegacyJava8.copyMap(blockIndex);
        VanillaInstincts.LOGGER.info(
                "Farmer Compatibility 2.0: {} culture(s) datapack chargée(s)",
                definitions.size());
    }

    public static int definitionCount() {
        return byItem.size();
    }

    public static CropDefinition definitionForItem(Item item) {
        return item == null ? null : byItem.get(item);
    }

    public static CropDefinition definitionForState(IBlockState state) {
        return state == null ? null : byBlock.get(state.getBlock());
    }

    public static Block cropBlockForItem(Item item) {
        CropDefinition definition = definitionForItem(item);
        if (definition != null) return definition.crop();
        // Preserve the exact vanilla mappings even if a datapack reload fails.
        if (item == Items.WHEAT_SEEDS) return Blocks.WHEAT;
        if (item == Items.CARROT) return Blocks.CARROTS;
        if (item == Items.POTATO) return Blocks.POTATOES;
        if (item == Items.BEETROOT_SEEDS) return Blocks.BEETROOTS;
        if (item instanceof ItemBlock
                && ((ItemBlock) (item)).getBlock() instanceof BlockCrops) { ItemBlock blockItem = (ItemBlock) (item); 
            return blockItem.getBlock();
        }
        return null;
    }

    public static IBlockState plantingState(Item item) {
        CropDefinition definition = definitionForItem(item);
        if (definition != null) return definition.plantingState();
        Block block = cropBlockForItem(item);
        return block == null ? null : block.getDefaultState();
    }

    public static boolean canPlant(Item item, WorldServer level,
                                   net.minecraft.util.math.BlockPos pos) {
        if (item == null || level == null || pos == null) return false;
        IBlockState planting = plantingState(item);
        if (planting == null) return false;
        CropDefinition definition = definitionForItem(item);
        if (definition != null && !definition.plantOn().isEmpty()
                && !definition.matchesGround(level.getBlockState(pos.down()))) {
            return false;
        }
        return planting.getBlock().canPlaceBlockAt(level, pos);
    }

    public static boolean hasPotentialPlantingGround(WorldServer level,
                                                     net.minecraft.util.math.BlockPos pos) {
        if (level == null || pos == null) return false;
        IBlockState occupant = level.getBlockState(pos);
        // A crop cell may contain a harmless replaceable state (for example a
        // thin snow layer or modded replaceable vegetation). Treat it like an
        // empty planting cell instead of silently rejecting the farmer action.
        if (!fr.vanillainstincts.compat.Minecraft112Compat.isAir(occupant) && !occupant.getMaterial().isReplaceable()) return false;
        IBlockState ground = level.getBlockState(pos.down());
        for (CropDefinition definition : byItem.values()) {
            if ((definition.plantOn().isEmpty()
                    || definition.matchesGround(ground))
                    && definition.plantingState().getBlock().canPlaceBlockAt(level, pos)) {
                return true;
            }
        }
        // Registry/tag fallback: datapacks and mods may extend valid soil
        // without requiring Java hardcodes.
        return Minecraft115TagCompat.blockStateIs(ground, VanillaInstinctsTags.FARMER_PLANTABLE_ON);
    }


    public static boolean isMature(IBlockState state) {
        if (state == null) return false;
        CropDefinition definition = definitionForState(state);
        if (definition != null) return definition.isMature(state);
        if (state.getBlock() instanceof BlockCrops) { BlockCrops crop = (BlockCrops) (state.getBlock()); 
            return crop.isMaxAge(state);
        }
        return false;
    }

    public static boolean shouldReplant(IBlockState state) {
        if (state == null) return false;
        CropDefinition definition = definitionForState(state);
        if (definition != null) return definition.replant();
        return state.getBlock() instanceof BlockCrops;
    }

    public static IBlockState harvestedReplacement(IBlockState harvested) {
        if (harvested == null) return null;
        CropDefinition definition = definitionForState(harvested);
        if (definition != null) {
            return definition.replant() ? definition.plantingState() : null;
        }
        if (harvested.getBlock() instanceof BlockCrops) { BlockCrops crop = (BlockCrops) (harvested.getBlock()); 
            return crop.getDefaultState();
        }
        return null;
    }

    private static CropDefinition parse(ResourceLocation id,
                                        JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("racine JSON attendue");
        }
        JsonObject root = element.getAsJsonObject();
        ResourceLocation itemId = requiredId(root, "item");
        ResourceLocation cropId = requiredId(root, "crop");
        Item item = LegacyRegistry.ITEM.get(itemId);
        Block crop = LegacyRegistry.BLOCK.get(cropId);
        if (!itemId.equals(LegacyRegistry.ITEM.getKey(item))) {
            throw new IllegalArgumentException("item inconnu: " + itemId);
        }
        if (!cropId.equals(LegacyRegistry.BLOCK.getKey(crop))) {
            throw new IllegalArgumentException("bloc culture inconnu: " + cropId);
        }
        List<BlockMatcher> plantOn = blockMatchers(root.get("plant_on"));
        String ageProperty = string(root, "age_property", null);
        Integer matureAge = root.has("mature_age")
                ? root.get("mature_age").getAsInt() : null;
        boolean replant = bool(root, "replant", true);
        int priority = integer(root, "priority", 0);
        return new CropDefinition(id, priority, item, crop, plantOn,
                ageProperty, matureAge, replant);
    }

    private static List<BlockMatcher> blockMatchers(JsonElement element) {
        if (element == null || element.isJsonNull()) return fr.vanillainstincts.compat.LegacyJava8.listOf();
        List<BlockMatcher> result = new ArrayList<>();
        if (element.isJsonPrimitive()) {
            result.add(BlockMatcher.parse(element.getAsString()));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> result.add(
                    BlockMatcher.parse(value.getAsString())));
        } else {
            throw new IllegalArgumentException(
                    "plant_on doit être une chaîne ou une liste");
        }
        return fr.vanillainstincts.compat.LegacyJava8.copyList(result);
    }

    private static ResourceLocation requiredId(JsonObject root, String key) {
        String raw = string(root, key, null);
        ResourceLocation id = raw == null ? null : fr.vanillainstincts.compat.LegacyResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException(
                    "identifiant requis/invalide: " + key);
        }
        return id;
    }

    private static String string(JsonObject root, String key,
                                 String fallback) {
        JsonElement value = root.get(key);
        return value == null || value.isJsonNull()
                ? fallback : value.getAsString();
    }

    private static boolean bool(JsonObject root, String key,
                                boolean fallback) {
        JsonElement value = root.get(key);
        return value == null ? fallback : value.getAsBoolean();
    }

    private static int integer(JsonObject root, String key, int fallback) {
        JsonElement value = root.get(key);
        return value == null ? fallback : value.getAsInt();
    }

    public static class CropDefinition {
        private final ResourceLocation id;
        private final int priority;
        private final Item item;
        private final Block crop;
        private final List<BlockMatcher> plantOn;
        private final String ageProperty;
        private final Integer matureAge;
        private final boolean replant;

        public CropDefinition(ResourceLocation id, int priority, Item item, Block crop, List<BlockMatcher> plantOn, String ageProperty, Integer matureAge, boolean replant) {
            this.id = id;
            this.priority = priority;
            this.item = item;
            this.crop = crop;
            this.plantOn = plantOn;
            this.ageProperty = ageProperty;
            this.matureAge = matureAge;
            this.replant = replant;
        }

        public ResourceLocation id() { return this.id; }

        public int priority() { return this.priority; }

        public Item item() { return this.item; }

        public Block crop() { return this.crop; }

        public List<BlockMatcher> plantOn() { return this.plantOn; }

        public String ageProperty() { return this.ageProperty; }

        public Integer matureAge() { return this.matureAge; }

        public boolean replant() { return this.replant; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CropDefinition)) return false;
            CropDefinition that = (CropDefinition) other;
            return java.util.Objects.equals(this.id, that.id) && this.priority == that.priority && java.util.Objects.equals(this.item, that.item) && java.util.Objects.equals(this.crop, that.crop) && java.util.Objects.equals(this.plantOn, that.plantOn) && java.util.Objects.equals(this.ageProperty, that.ageProperty) && java.util.Objects.equals(this.matureAge, that.matureAge) && this.replant == that.replant;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.id, this.priority, this.item, this.crop, this.plantOn, this.ageProperty, this.matureAge, this.replant); }

        @Override
        public String toString() {
            return "CropDefinition[" + "id=" + this.id + ", " + "priority=" + this.priority + ", " + "item=" + this.item + ", " + "crop=" + this.crop + ", " + "plantOn=" + this.plantOn + ", " + "ageProperty=" + this.ageProperty + ", " + "matureAge=" + this.matureAge + ", " + "replant=" + this.replant + "]";
        }

        public boolean matchesGround(IBlockState ground) {
            if (plantOn.isEmpty()) return true;
            for (BlockMatcher matcher : plantOn) {
                if (matcher.matches(ground)) return true;
            }
            return false;
        }

        public boolean isMature(IBlockState state) {
            if (state == null || state.getBlock() != crop) return false;
            if (ageProperty == null || ageProperty.trim().isEmpty()) {
                return crop instanceof BlockCrops
                        && ((BlockCrops) (crop)).isMaxAge(state);
            }
            IProperty<?> property = state.getBlock().getBlockState()
                    .getProperty(ageProperty);
            if (!(property instanceof PropertyInteger)) {
                return false;
            } PropertyInteger integerProperty = (PropertyInteger) (property);
            int current = state.getValue(integerProperty);
            int minimum = java.util.Collections.min(integerProperty.getAllowedValues());
            int maximum = java.util.Collections.max(integerProperty.getAllowedValues());
            int requested = matureAge == null ? maximum : matureAge;
            int target = Math.max(minimum, Math.min(maximum, requested));
            return current >= target;
        }

        public IBlockState plantingState() {
            IBlockState state = crop.getDefaultState();
            if (ageProperty == null || ageProperty.trim().isEmpty()) return state;
            IProperty<?> property = crop.getBlockState().getProperty(
                    ageProperty);
            if (property instanceof PropertyInteger) { PropertyInteger integerProperty = (PropertyInteger) (property); 
                int minimum = java.util.Collections.min(integerProperty.getAllowedValues());
                if (integerProperty.getAllowedValues().contains(minimum)) {
                    state = state.withProperty(integerProperty, minimum);
                }
            }
            return state;
        }
    }

    public static class BlockMatcher {
        private final boolean tag;
        private final ResourceLocation id;

        public BlockMatcher(boolean tag, ResourceLocation id) {
            this.tag = tag;
            this.id = id;
        }

        public boolean tag() { return this.tag; }

        public ResourceLocation id() { return this.id; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BlockMatcher)) return false;
            BlockMatcher that = (BlockMatcher) other;
            return this.tag == that.tag && java.util.Objects.equals(this.id, that.id);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.tag, this.id); }

        @Override
        public String toString() {
            return "BlockMatcher[" + "tag=" + this.tag + ", " + "id=" + this.id + "]";
        }

        static BlockMatcher parse(String selector) {
            if (selector == null || selector.trim().isEmpty()) {
                throw new IllegalArgumentException("sélecteur plant_on vide");
            }
            boolean tag = selector.charAt(0) == '#';
            String rawId = tag ? selector.substring(1) : selector;
            ResourceLocation id = fr.vanillainstincts.compat.LegacyResourceLocation.tryParse(rawId);
            if (id == null) {
                throw new IllegalArgumentException(
                        "bloc/tag plant_on invalide: " + selector);
            }
            return new BlockMatcher(tag, id);
        }

        boolean matches(IBlockState state) {
            if (state == null) return false;
            if (tag) return Minecraft115TagCompat.blockStateIs(state, Minecraft115TagCompat.blockTag(id));
            return id.equals(LegacyRegistry.BLOCK.getKey(state.getBlock()));
        }
    }
}

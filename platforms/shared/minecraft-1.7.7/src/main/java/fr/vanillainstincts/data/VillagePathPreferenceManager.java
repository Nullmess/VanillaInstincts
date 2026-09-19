package fr.vanillainstincts.data;

import fr.vanillainstincts.compat.Minecraft115TagCompat;

import fr.vanillainstincts.compat.LegacyRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.vanillainstincts.VanillaInstincts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.block.Block;
import fr.vanillainstincts.compat.LegacyBlockState;

/**
 * Data-driven static path preferences used by villager destination scoring.
 *
 * <p>Files live in {@code data/<namespace>/villager_path_preferences/*.json}.
 * They never replace vanilla navigation: they only add bounded, deterministic
 * costs to candidate positions that Vanilla Instincts already asks vanilla to
 * navigate toward.</p>
 */
public final class VillagePathPreferenceManager {
    private static final Gson GSON = new GsonBuilder().create();
    private static final int CACHE_SOFT_LIMIT = 8_192;
    private static volatile List<PathRule> rules = fr.vanillainstincts.compat.LegacyJava8.listOf();
    private static final Map<CacheKey, Double> STATIC_CACHE =
            new ConcurrentHashMap<>();

    public VillagePathPreferenceManager() {}

    private void apply(Map<ResourceLocation, JsonObject> objects) {
        List<PathRule> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonObject> entry : objects.entrySet()) {
            try {
                PathRule rule = parse(entry.getKey(), entry.getValue());
                if (rule != null) loaded.add(rule);
            } catch (RuntimeException exception) {
                VanillaInstincts.LOGGER.warn(
                        "Préférence de chemin villageois ignorée ({}): {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        loaded.sort(Comparator.comparingInt(PathRule::priority).reversed()
                .thenComparing(rule -> rule.id().toString()));
        rules = fr.vanillainstincts.compat.LegacyJava8.copyList(loaded);
        STATIC_CACHE.clear();
        VanillaInstincts.LOGGER.info(
                "Villager Pathfinding 2.0: {} règle(s) datapack chargée(s)",
                rules.size());
    }

    public static int ruleCount() {
        return rules.size();
    }

    /** Returns only static block/profession costs; world-state bonuses stay outside the cache. */
    public static double staticPreference(LegacyVillagerProfession profession,
                                          LegacyBlockState feet,
                                          LegacyBlockState floor) {
        if (profession == null || feet == null || floor == null) return 0.0D;
        ResourceLocation professionId = LegacyRegistry.VILLAGER_PROFESSION
                .getKey(profession);
        ResourceLocation feetId = LegacyRegistry.BLOCK.getKey(feet.getBlock());
        ResourceLocation floorId = LegacyRegistry.BLOCK.getKey(floor.getBlock());
        CacheKey key = new CacheKey(professionId, feetId, floorId);
        Double cached = STATIC_CACHE.get(key);
        if (cached != null) return cached;

        double score = 0.0D;
        for (PathRule rule : rules) {
            if (rule.matchesProfession(professionId)) {
                score += rule.score(feet, floor);
            }
        }
        if (STATIC_CACHE.size() >= CACHE_SOFT_LIMIT) {
            STATIC_CACHE.clear();
        }
        score = clamp(score, -64.0D, 64.0D);
        STATIC_CACHE.put(key, score);
        return score;
    }

    /** Dynamic bonuses cannot be cached because they depend on nearby fluid/world state. */
    public static double adjacentWaterBonus(LegacyVillagerProfession profession) {
        if (profession == null) return 0.0D;
        ResourceLocation professionId = LegacyRegistry.VILLAGER_PROFESSION
                .getKey(profession);
        double bonus = 0.0D;
        for (PathRule rule : rules) {
            if (rule.matchesProfession(professionId)) {
                bonus += rule.adjacentWaterBonus();
            }
        }
        return bonus;
    }

    /** Bounded dynamic penalty for blocks near a candidate path position. */
    public static double proximityPreference(LegacyVillagerProfession profession,
                                             WorldServer level,
                                             BlockPos feet) {
        if (profession == null || level == null || feet == null) return 0.0D;
        ResourceLocation professionId = LegacyRegistry.VILLAGER_PROFESSION
                .getKey(profession);
        double score = 0.0D;
        for (PathRule rule : rules) {
            if (rule.matchesProfession(professionId)) {
                score += rule.proximityScore(level, feet);
            }
        }
        return clamp(score, -64.0D, 0.0D);
    }

    private static PathRule parse(ResourceLocation id, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("racine JSON attendue");
        }
        JsonObject root = element.getAsJsonObject();
        int priority = integer(root, "priority", 0);
        Set<String> professions = stringSet(root.get("professions"), "*");
        Set<String> excluded = stringSet(root.get("exclude_professions"), null);
        List<WeightedBlockMatcher> walkOn = weightedMatchers(root, "walk_on");
        List<WeightedBlockMatcher> walkThrough = weightedMatchers(root,
                "walk_through");
        List<WeightedBlockMatcher> avoid = weightedMatchers(root, "avoid");
        List<WeightedBlockMatcher> actionCost = weightedMatchers(root,
                "action_cost");
        List<NearBlockMatcher> avoidNear = nearMatchers(root, "avoid_near");
        double waterBonus = clamp(number(root, "adjacent_water_bonus", 0.0D),
                -16.0D, 16.0D);
        return new PathRule(id, priority, professions, excluded, walkOn,
                walkThrough, avoid, actionCost, avoidNear, waterBonus);
    }

    private static List<WeightedBlockMatcher> weightedMatchers(JsonObject root,
                                                                String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) return fr.vanillainstincts.compat.LegacyJava8.listOf();
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(key + " doit être un objet");
        }
        List<WeightedBlockMatcher> result = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry
                : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(
                        key + "." + entry.getKey() + " doit être numérique");
            }
            double weight = entry.getValue().getAsDouble();
            if (!Double.isFinite(weight)) {
                throw new IllegalArgumentException(
                        key + "." + entry.getKey() + " doit être fini");
            }
            result.add(BlockMatcher.parse(entry.getKey(),
                    clamp(weight, -16.0D, 16.0D)));
        }
        return fr.vanillainstincts.compat.LegacyJava8.copyList(result);
    }

    private static List<NearBlockMatcher> nearMatchers(JsonObject root,
                                                       String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) return fr.vanillainstincts.compat.LegacyJava8.listOf();
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(key + " doit être un objet");
        }
        List<NearBlockMatcher> result = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry
                : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            int radius = 2;
            double weight;
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                weight = value.getAsDouble();
            } else if (value.isJsonObject()) {
                JsonObject settings = value.getAsJsonObject();
                radius = integer(settings, "radius", 2);
                weight = number(settings, "weight", 4.0D);
            } else {
                throw new IllegalArgumentException(
                        key + "." + entry.getKey() + " doit être un nombre ou objet");
            }
            radius = Math.max(1, Math.min(6, radius));
            weight = clamp(Math.abs(weight), 0.0D, 16.0D);
            result.add(new NearBlockMatcher(
                    BlockMatcher.parse(entry.getKey(), weight), radius));
        }
        return fr.vanillainstincts.compat.LegacyJava8.copyList(result);
    }

    private static Set<String> stringSet(JsonElement element,
                                         String defaultValue) {
        if (element == null || element.isJsonNull()) {
            return defaultValue == null ? fr.vanillainstincts.compat.LegacyJava8.setOf() : fr.vanillainstincts.compat.LegacyJava8.setOf(defaultValue);
        }
        Set<String> result = new HashSet<>();
        if (element.isJsonPrimitive()) {
            result.add(element.getAsString());
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> result.add(
                    value.getAsString()));
        } else {
            throw new IllegalArgumentException("liste ou chaîne attendue");
        }
        return fr.vanillainstincts.compat.LegacyJava8.copySet(result);
    }

    private static int integer(JsonObject root, String key, int fallback) {
        JsonElement value = root.get(key);
        return value == null ? fallback : value.getAsInt();
    }

    private static double number(JsonObject root, String key,
                                 double fallback) {
        JsonElement value = root.get(key);
        return value == null ? fallback : value.getAsDouble();
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return 0.0D;
        return Math.max(min, Math.min(max, value));
    }

    private static class CacheKey {
        private final ResourceLocation profession;
        private final ResourceLocation feet;
        private final ResourceLocation floor;

        public CacheKey(ResourceLocation profession, ResourceLocation feet, ResourceLocation floor) {
            this.profession = profession;
            this.feet = feet;
            this.floor = floor;
        }

        public ResourceLocation profession() { return this.profession; }

        public ResourceLocation feet() { return this.feet; }

        public ResourceLocation floor() { return this.floor; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CacheKey)) return false;
            CacheKey that = (CacheKey) other;
            return java.util.Objects.equals(this.profession, that.profession) && java.util.Objects.equals(this.feet, that.feet) && java.util.Objects.equals(this.floor, that.floor);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.profession, this.feet, this.floor); }

        @Override
        public String toString() {
            return "CacheKey[" + "profession=" + this.profession + ", " + "feet=" + this.feet + ", " + "floor=" + this.floor + "]";
        }

    }

    private static class PathRule {
        private final ResourceLocation id;
        private final int priority;
        private final Set<String> professions;
        private final Set<String> excludedProfessions;
        private final List<WeightedBlockMatcher> walkOn;
        private final List<WeightedBlockMatcher> walkThrough;
        private final List<WeightedBlockMatcher> avoid;
        private final List<WeightedBlockMatcher> actionCost;
        private final List<NearBlockMatcher> avoidNear;
        private final double adjacentWaterBonus;

        public PathRule(ResourceLocation id, int priority, Set<String> professions, Set<String> excludedProfessions, List<WeightedBlockMatcher> walkOn, List<WeightedBlockMatcher> walkThrough, List<WeightedBlockMatcher> avoid, List<WeightedBlockMatcher> actionCost, List<NearBlockMatcher> avoidNear, double adjacentWaterBonus) {
            this.id = id;
            this.priority = priority;
            this.professions = professions;
            this.excludedProfessions = excludedProfessions;
            this.walkOn = walkOn;
            this.walkThrough = walkThrough;
            this.avoid = avoid;
            this.actionCost = actionCost;
            this.avoidNear = avoidNear;
            this.adjacentWaterBonus = adjacentWaterBonus;
        }

        public ResourceLocation id() { return this.id; }

        public int priority() { return this.priority; }

        public Set<String> professions() { return this.professions; }

        public Set<String> excludedProfessions() { return this.excludedProfessions; }

        public List<WeightedBlockMatcher> walkOn() { return this.walkOn; }

        public List<WeightedBlockMatcher> walkThrough() { return this.walkThrough; }

        public List<WeightedBlockMatcher> avoid() { return this.avoid; }

        public List<WeightedBlockMatcher> actionCost() { return this.actionCost; }

        public List<NearBlockMatcher> avoidNear() { return this.avoidNear; }

        public double adjacentWaterBonus() { return this.adjacentWaterBonus; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PathRule)) return false;
            PathRule that = (PathRule) other;
            return java.util.Objects.equals(this.id, that.id) && this.priority == that.priority && java.util.Objects.equals(this.professions, that.professions) && java.util.Objects.equals(this.excludedProfessions, that.excludedProfessions) && java.util.Objects.equals(this.walkOn, that.walkOn) && java.util.Objects.equals(this.walkThrough, that.walkThrough) && java.util.Objects.equals(this.avoid, that.avoid) && java.util.Objects.equals(this.actionCost, that.actionCost) && java.util.Objects.equals(this.avoidNear, that.avoidNear) && Double.compare(this.adjacentWaterBonus, that.adjacentWaterBonus) == 0;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.id, this.priority, this.professions, this.excludedProfessions, this.walkOn, this.walkThrough, this.avoid, this.actionCost, this.avoidNear, this.adjacentWaterBonus); }

        @Override
        public String toString() {
            return "PathRule[" + "id=" + this.id + ", " + "priority=" + this.priority + ", " + "professions=" + this.professions + ", " + "excludedProfessions=" + this.excludedProfessions + ", " + "walkOn=" + this.walkOn + ", " + "walkThrough=" + this.walkThrough + ", " + "avoid=" + this.avoid + ", " + "actionCost=" + this.actionCost + ", " + "avoidNear=" + this.avoidNear + ", " + "adjacentWaterBonus=" + this.adjacentWaterBonus + "]";
        }

        boolean matchesProfession(ResourceLocation professionId) {
            String idString = professionId == null ? "" : professionId.toString();
            if (excludedProfessions.contains(idString)) return false;
            return professions.contains("*") || professions.contains(idString);
        }

        double score(LegacyBlockState feet, LegacyBlockState floor) {
            double score = sum(walkOn, floor) + sum(walkThrough, feet);
            score -= Math.abs(sum(avoid, floor));
            score -= Math.abs(sum(avoid, feet));
            score -= Math.abs(sum(actionCost, floor));
            score -= Math.abs(sum(actionCost, feet));
            return score;
        }

        double proximityScore(WorldServer level, BlockPos feet) {
            double score = 0.0D;
            for (NearBlockMatcher matcher : avoidNear) {
                int radius = matcher.radius();
                int bestDistance = Integer.MAX_VALUE;
                search:
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int horizontal = Math.abs(dx) + Math.abs(dz);
                        if (horizontal > radius) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos probe = fr.vanillainstincts.compat.Minecraft112Compat.offset(feet, dx, dy, dz);
                            if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, probe)) continue;
                            if (matcher.matcher().matches(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, probe))) {
                                bestDistance = Math.min(bestDistance, horizontal);
                                if (bestDistance == 0) break search;
                            }
                        }
                    }
                }
                if (bestDistance != Integer.MAX_VALUE) {
                    double closeness = (radius + 1.0D - bestDistance)
                            / (radius + 1.0D);
                    score -= Math.abs(matcher.matcher().weight()) * closeness;
                }
            }
            return score;
        }

        private static double sum(List<WeightedBlockMatcher> matchers,
                                  LegacyBlockState state) {
            double value = 0.0D;
            for (WeightedBlockMatcher matcher : matchers) {
                if (matcher.matches(state)) value += matcher.weight();
            }
            return value;
        }
    }

    private static class NearBlockMatcher {
        private final WeightedBlockMatcher matcher;
        private final int radius;

        public NearBlockMatcher(WeightedBlockMatcher matcher, int radius) {
            this.matcher = matcher;
            this.radius = radius;
        }

        public WeightedBlockMatcher matcher() { return this.matcher; }

        public int radius() { return this.radius; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NearBlockMatcher)) return false;
            NearBlockMatcher that = (NearBlockMatcher) other;
            return java.util.Objects.equals(this.matcher, that.matcher) && this.radius == that.radius;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.matcher, this.radius); }

        @Override
        public String toString() {
            return "NearBlockMatcher[" + "matcher=" + this.matcher + ", " + "radius=" + this.radius + "]";
        }

    }

    private static class WeightedBlockMatcher {
        private final boolean tag;
        private final ResourceLocation id;
        private final double weight;

        public WeightedBlockMatcher(boolean tag, ResourceLocation id, double weight) {
            this.tag = tag;
            this.id = id;
            this.weight = weight;
        }

        public boolean tag() { return this.tag; }

        public ResourceLocation id() { return this.id; }

        public double weight() { return this.weight; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof WeightedBlockMatcher)) return false;
            WeightedBlockMatcher that = (WeightedBlockMatcher) other;
            return this.tag == that.tag && java.util.Objects.equals(this.id, that.id) && Double.compare(this.weight, that.weight) == 0;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.tag, this.id, this.weight); }

        @Override
        public String toString() {
            return "WeightedBlockMatcher[" + "tag=" + this.tag + ", " + "id=" + this.id + ", " + "weight=" + this.weight + "]";
        }

        boolean matches(LegacyBlockState state) {
            if (state == null) return false;
            if (tag) {
                return Minecraft115TagCompat.blockStateIs(state, Minecraft115TagCompat.blockTag(id));
            }
            return id.equals(LegacyRegistry.BLOCK.getKey(state.getBlock()));
        }
    }

    private static final class BlockMatcher {
        private BlockMatcher() {
        }

        static WeightedBlockMatcher parse(String selector, double weight) {
            if (selector == null || selector.trim().isEmpty()) {
                throw new IllegalArgumentException("sélecteur de bloc vide");
            }
            boolean tag = selector.charAt(0) == '#';
            String rawId = tag ? selector.substring(1) : selector;
            ResourceLocation id = fr.vanillainstincts.compat.LegacyResourceLocation.tryParse(rawId);
            if (id == null) {
                throw new IllegalArgumentException(
                        "identifiant de bloc/tag invalide: " + selector);
            }
            return new WeightedBlockMatcher(tag, id, weight);
        }
    }
}

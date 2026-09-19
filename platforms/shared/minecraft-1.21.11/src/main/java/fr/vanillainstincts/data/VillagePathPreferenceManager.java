package fr.vanillainstincts.data;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Data-driven static path preferences used by villager destination scoring.
 *
 * <p>Files live in {@code data/<namespace>/villager_path_preferences/*.json}.
 * They never replace vanilla navigation: they only add bounded, deterministic
 * costs to candidate positions that Vanilla Instincts already asks vanilla to
 * navigate toward.</p>
 */
public final class VillagePathPreferenceManager
        extends VanillaJsonReloadListener {
    private static final int CACHE_SOFT_LIMIT = 8_192;
    private static volatile List<PathRule> rules = List.of();
    private static final Map<CacheKey, Double> STATIC_CACHE =
            new ConcurrentHashMap<>();

    public VillagePathPreferenceManager() {
        super("villager_path_preferences");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<PathRule> loaded = new ArrayList<>();
        for (Map.Entry<Identifier, JsonElement> entry : objects.entrySet()) {
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
        rules = List.copyOf(loaded);
        STATIC_CACHE.clear();
        VanillaInstincts.LOGGER.info(
                "Villager Pathfinding 2.0: {} règle(s) datapack chargée(s)",
                rules.size());
    }

    public static int ruleCount() {
        return rules.size();
    }

    /** Returns only static block/profession costs; world-state bonuses stay outside the cache. */
    public static double staticPreference(VillagerProfession profession,
                                          BlockState feet,
                                          BlockState floor) {
        if (profession == null || feet == null || floor == null) return 0.0D;
        Identifier professionId = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(profession);
        Identifier feetId = BuiltInRegistries.BLOCK.getKey(feet.getBlock());
        Identifier floorId = BuiltInRegistries.BLOCK.getKey(floor.getBlock());
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
    public static double adjacentWaterBonus(VillagerProfession profession) {
        if (profession == null) return 0.0D;
        Identifier professionId = BuiltInRegistries.VILLAGER_PROFESSION
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
    public static double proximityPreference(VillagerProfession profession,
                                             ServerLevel level,
                                             BlockPos feet) {
        if (profession == null || level == null || feet == null) return 0.0D;
        Identifier professionId = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(profession);
        double score = 0.0D;
        for (PathRule rule : rules) {
            if (rule.matchesProfession(professionId)) {
                score += rule.proximityScore(level, feet);
            }
        }
        return clamp(score, -64.0D, 0.0D);
    }

    private static PathRule parse(Identifier id, JsonElement element) {
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
        if (element == null || element.isJsonNull()) return List.of();
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
        return List.copyOf(result);
    }

    private static List<NearBlockMatcher> nearMatchers(JsonObject root,
                                                       String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) return List.of();
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
        return List.copyOf(result);
    }

    private static Set<String> stringSet(JsonElement element,
                                         String defaultValue) {
        if (element == null || element.isJsonNull()) {
            return defaultValue == null ? Set.of() : Set.of(defaultValue);
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
        return Set.copyOf(result);
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

    private record CacheKey(Identifier profession,
                            Identifier feet,
                            Identifier floor) {
    }

    private record PathRule(Identifier id,
                            int priority,
                            Set<String> professions,
                            Set<String> excludedProfessions,
                            List<WeightedBlockMatcher> walkOn,
                            List<WeightedBlockMatcher> walkThrough,
                            List<WeightedBlockMatcher> avoid,
                            List<WeightedBlockMatcher> actionCost,
                            List<NearBlockMatcher> avoidNear,
                            double adjacentWaterBonus) {
        boolean matchesProfession(Identifier professionId) {
            String idString = professionId == null ? "" : professionId.toString();
            if (excludedProfessions.contains(idString)) return false;
            return professions.contains("*") || professions.contains(idString);
        }

        double score(BlockState feet, BlockState floor) {
            double score = sum(walkOn, floor) + sum(walkThrough, feet);
            score -= Math.abs(sum(avoid, floor));
            score -= Math.abs(sum(avoid, feet));
            score -= Math.abs(sum(actionCost, floor));
            score -= Math.abs(sum(actionCost, feet));
            return score;
        }

        double proximityScore(ServerLevel level, BlockPos feet) {
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
                            BlockPos probe = feet.offset(dx, dy, dz);
                            if (!level.hasChunkAt(probe)) continue;
                            if (matcher.matcher().matches(level.getBlockState(probe))) {
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
                                  BlockState state) {
            double value = 0.0D;
            for (WeightedBlockMatcher matcher : matchers) {
                if (matcher.matches(state)) value += matcher.weight();
            }
            return value;
        }
    }

    private record NearBlockMatcher(WeightedBlockMatcher matcher,
                                    int radius) {
    }

    private record WeightedBlockMatcher(boolean tag,
                                        Identifier id,
                                        double weight) {
        boolean matches(BlockState state) {
            if (state == null) return false;
            if (tag) {
                return state.is(TagKey.create(Registries.BLOCK, id));
            }
            return id.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        }
    }

    private static final class BlockMatcher {
        private BlockMatcher() {
        }

        static WeightedBlockMatcher parse(String selector, double weight) {
            if (selector == null || selector.isBlank()) {
                throw new IllegalArgumentException("sélecteur de bloc vide");
            }
            boolean tag = selector.charAt(0) == '#';
            String rawId = tag ? selector.substring(1) : selector;
            Identifier id = Identifier.tryParse(rawId);
            if (id == null) {
                throw new IllegalArgumentException(
                        "identifiant de bloc/tag invalide: " + selector);
            }
            return new WeightedBlockMatcher(tag, id, weight);
        }
    }
}

package fr.vanillainstincts.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.model.MobRelationType;
import fr.vanillainstincts.core.rules.AcquisitionRules;
import fr.vanillainstincts.core.rules.EcologyRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Reload-safe data-driven relation graph used by Acquisition 3.0 and Mob
 * Ecology 2.0. A relation never bypasses vanilla attackability or perception;
 * it only describes what an observer may want to do after legitimately
 * detecting a candidate.
 */
public final class MobRelationManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile List<RelationRule> rules = List.of();
    private static final Map<EntityType<?>, Double> MAX_HUNT_DISTANCE =
            new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, Double> MAX_RELATION_DISTANCE =
            new ConcurrentHashMap<>();

    public MobRelationManager() {
        super(GSON, "mob_relations");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<RelationRule> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            try {
                RelationRule rule = parse(entry.getKey(), entry.getValue());
                if (rule != null) loaded.add(rule);
            } catch (RuntimeException exception) {
                VanillaInstincts.LOGGER.warn(
                        "Relation d'écologie ignorée ({}): {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        loaded.sort(Comparator.comparingInt(RelationRule::priority).reversed()
                .thenComparing(rule -> rule.id().toString()));
        rules = List.copyOf(loaded);
        MAX_HUNT_DISTANCE.clear();
        MAX_RELATION_DISTANCE.clear();
        VanillaInstincts.LOGGER.info(
                "Intelligence Core 3.0: {} relation(s) mob datapack chargée(s)",
                rules.size());
    }

    public static int ruleCount() {
        return rules.size();
    }

    public static Optional<RelationDecision> relation(Mob observer,
                                                       LivingEntity target) {
        if (observer == null || target == null || observer == target) {
            return Optional.empty();
        }
        EntityType<?> observerType = observer.getType();
        EntityType<?> targetType = target.getType();
        for (RelationRule rule : rules) {
            if (rule.matches(observerType, targetType)) {
                return Optional.of(rule.decision());
            }
        }
        return Optional.empty();
    }

    public static double maximumHuntDistance(Mob observer) {
        if (observer == null) return 0.0D;
        return MAX_HUNT_DISTANCE.computeIfAbsent(observer.getType(), type -> {
            double maximum = 0.0D;
            for (RelationRule rule : rules) {
                if (rule.relation() == MobRelationType.HUNT
                        && rule.matchesObserver(type)) {
                    maximum = Math.max(maximum, rule.maxDistance());
                }
            }
            return clamp(maximum, 0.0D, AcquisitionRules.MAX_SCAN_RADIUS);
        });
    }

    public static double maximumRelationDistance(Mob observer) {
        if (observer == null) return 0.0D;
        return MAX_RELATION_DISTANCE.computeIfAbsent(observer.getType(), type -> {
            double maximum = 0.0D;
            for (RelationRule rule : rules) {
                if (rule.relation() != MobRelationType.IGNORE
                        && rule.matchesObserver(type)) {
                    maximum = Math.max(maximum, rule.maxDistance());
                }
            }
            return clamp(maximum, 0.0D, EcologyRules.MAX_RELATION_RADIUS);
        });
    }

    private static RelationRule parse(ResourceLocation id, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("racine JSON attendue");
        }
        JsonObject root = element.getAsJsonObject();
        int priority = integer(root, "priority", 0);
        List<EntityMatcher> observers = matchers(root.get("observers"));
        List<EntityMatcher> targets = matchers(root.get("targets"));
        String relationName = string(root, "relation", "IGNORE");
        MobRelationType relation;
        try {
            relation = MobRelationType.valueOf(relationName.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("relation inconnue: " + relationName);
        }
        double maxDistance = clamp(number(root, "max_distance", 16.0D),
                1.0D, Math.max(AcquisitionRules.MAX_SCAN_RADIUS,
                        EcologyRules.MAX_RELATION_RADIUS));
        boolean requiresLineOfSight = bool(root, "requires_line_of_sight", true);
        double weight = clamp(number(root, "weight", 1.0D), 0.0D, 8.0D);
        return new RelationRule(id, priority, observers, targets, relation,
                maxDistance, requiresLineOfSight, weight);
    }

    private static List<EntityMatcher> matchers(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of(EntityMatcher.any());
        }
        Set<String> selectors = new HashSet<>();
        if (element.isJsonPrimitive()) {
            selectors.add(element.getAsString());
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> selectors.add(
                    value.getAsString()));
        } else {
            throw new IllegalArgumentException(
                    "sélecteur d'entité doit être une chaîne ou une liste");
        }
        List<EntityMatcher> result = new ArrayList<>();
        for (String selector : selectors) result.add(EntityMatcher.parse(selector));
        return List.copyOf(result);
    }

    private static int integer(JsonObject root, String key, int fallback) {
        JsonElement value = root.get(key);
        return value == null ? fallback : value.getAsInt();
    }

    private static double number(JsonObject root, String key, double fallback) {
        JsonElement value = root.get(key);
        return value == null ? fallback : value.getAsDouble();
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        JsonElement value = root.get(key);
        return value == null ? fallback : value.getAsBoolean();
    }

    private static String string(JsonObject root, String key, String fallback) {
        JsonElement value = root.get(key);
        return value == null ? fallback : value.getAsString();
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record RelationDecision(MobRelationType relation,
                                   int priority,
                                   double maxDistance,
                                   boolean requiresLineOfSight,
                                   double weight,
                                   ResourceLocation source) {
    }

    private record RelationRule(ResourceLocation id,
                                int priority,
                                List<EntityMatcher> observers,
                                List<EntityMatcher> targets,
                                MobRelationType relation,
                                double maxDistance,
                                boolean requiresLineOfSight,
                                double weight) {
        boolean matches(EntityType<?> observer, EntityType<?> target) {
            return matchesObserver(observer) && matchesAny(targets, target);
        }

        boolean matchesObserver(EntityType<?> observer) {
            return matchesAny(observers, observer);
        }

        RelationDecision decision() {
            return new RelationDecision(relation, priority, maxDistance,
                    requiresLineOfSight, weight, id);
        }

        private static boolean matchesAny(List<EntityMatcher> matchers,
                                          EntityType<?> type) {
            for (EntityMatcher matcher : matchers) {
                if (matcher.matches(type)) return true;
            }
            return false;
        }
    }

    private record EntityMatcher(boolean wildcard,
                                 boolean tag,
                                 ResourceLocation id) {
        static EntityMatcher any() {
            return new EntityMatcher(true, false, null);
        }

        static EntityMatcher parse(String selector) {
            if (selector == null || selector.isBlank() || "*".equals(selector)) {
                return any();
            }
            boolean tag = selector.charAt(0) == '#';
            String rawId = tag ? selector.substring(1) : selector;
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id == null) {
                throw new IllegalArgumentException(
                        "identifiant d'entité/tag invalide: " + selector);
            }
            return new EntityMatcher(false, tag, id);
        }

        boolean matches(EntityType<?> type) {
            if (wildcard) return true;
            if (tag) {
                return type.is(TagKey.create(Registries.ENTITY_TYPE, id));
            }
            return id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        }
    }
}

package fr.vanillainstincts.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.vanillainstincts.VanillaInstincts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;

/**
 * Species-aware Animal Welfare 3.0 settings loaded from datapacks.
 *
 * <p>Files live in {@code data/<namespace>/animal_welfare/*.json}. Profiles
 * only tune bounded local observations; they never reveal unloaded or hidden
 * information.</p>
 */
public final class AnimalWelfareProfileManager
        extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile List<WelfareProfile> profiles = List.of();

    private static final WelfareProfile DEFAULT = new WelfareProfile(
            ResourceLocation.fromNamespaceAndPath(VanillaInstincts.MOD_ID,
                    "default"), Integer.MIN_VALUE, List.of(EntityMatcher.any()),
            true, true, true, true, 1.0D, 12_000L, 23_000L,
            58, 72, 320L, 52);

    public AnimalWelfareProfileManager() {
        super(GSON, "animal_welfare");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<WelfareProfile> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            try {
                loaded.add(parse(entry.getKey(), entry.getValue()));
            } catch (RuntimeException exception) {
                VanillaInstincts.LOGGER.warn(
                        "Profil Animal Welfare ignoré ({}): {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        loaded.sort(Comparator.comparingInt(WelfareProfile::priority).reversed()
                .thenComparing(profile -> profile.id().toString()));
        profiles = List.copyOf(loaded);
        VanillaInstincts.LOGGER.info(
                "Animal Welfare 3.0: {} profil(s) datapack chargé(s)",
                profiles.size());
    }

    public static int profileCount() {
        return profiles.size();
    }

    public static WelfareProfile profileFor(Animal animal) {
        if (animal == null) return DEFAULT;
        EntityType<?> type = animal.getType();
        for (WelfareProfile profile : profiles) {
            if (profile.matches(type)) return profile;
        }
        return DEFAULT;
    }

    private static WelfareProfile parse(ResourceLocation id,
                                        JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("racine JSON attendue");
        }
        JsonObject root = element.getAsJsonObject();
        List<EntityMatcher> animals = matchers(root.get("animals"));
        int priority = integer(root, "priority", 0);
        boolean shelter = bool(root, "needs_shelter", true);
        boolean water = bool(root, "needs_nearby_water", true);
        boolean herd = bool(root, "needs_herd", true);
        boolean rest = bool(root, "needs_rest", true);
        double hotTemperature = clamp(number(root, "hot_temperature", 1.0D),
                -2.0D, 2.0D);
        long restStart = boundedLong(root, "rest_start", 12_000L, 0L, 23_999L);
        long restEnd = boundedLong(root, "rest_end", 23_000L, 0L, 23_999L);
        int stressThreshold = clamp(integer(root, "stress_threshold", 58),
                1, 99);
        int recoveryThreshold = clamp(integer(root, "recovery_threshold", 72),
                stressThreshold, 100);
        long recoveryTicks = boundedLong(root, "recovery_ticks", 320L,
                20L, 24_000L);
        int breedingMinimum = clamp(integer(root, "breeding_min_score", 52),
                0, 100);
        return new WelfareProfile(id, priority, animals, shelter, water, herd,
                rest, hotTemperature, restStart, restEnd, stressThreshold,
                recoveryThreshold, recoveryTicks, breedingMinimum);
    }

    private static List<EntityMatcher> matchers(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of(EntityMatcher.any());
        }
        Set<String> values = new HashSet<>();
        if (element.isJsonPrimitive()) {
            values.add(element.getAsString());
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> values.add(
                    value.getAsString()));
        } else {
            throw new IllegalArgumentException(
                    "animals doit être une chaîne ou une liste");
        }
        List<EntityMatcher> result = new ArrayList<>();
        for (String value : values) result.add(EntityMatcher.parse(value));
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

    private static long boundedLong(JsonObject root, String key, long fallback,
                                    long minimum, long maximum) {
        JsonElement value = root.get(key);
        long parsed = value == null ? fallback : value.getAsLong();
        return Math.max(minimum, Math.min(maximum, parsed));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record WelfareProfile(ResourceLocation id,
                                 int priority,
                                 List<EntityMatcher> animals,
                                 boolean needsShelter,
                                 boolean needsNearbyWater,
                                 boolean needsHerd,
                                 boolean needsRest,
                                 double hotTemperature,
                                 long restStart,
                                 long restEnd,
                                 int stressThreshold,
                                 int recoveryThreshold,
                                 long recoveryTicks,
                                 int breedingMinimumScore) {
        public boolean matches(EntityType<?> type) {
            for (EntityMatcher matcher : animals) {
                if (matcher.matches(type)) return true;
            }
            return false;
        }

        public boolean restPeriod(long dayTime) {
            if (!needsRest) return false;
            long time = Math.floorMod(dayTime, 24_000L);
            if (restStart <= restEnd) {
                return time >= restStart && time <= restEnd;
            }
            return time >= restStart || time <= restEnd;
        }
    }

    public record EntityMatcher(boolean wildcard,
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
            String raw = tag ? selector.substring(1) : selector;
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) {
                throw new IllegalArgumentException(
                        "identifiant animal/tag invalide: " + selector);
            }
            return new EntityMatcher(false, tag, id);
        }

        boolean matches(EntityType<?> type) {
            if (type == null) return false;
            if (wildcard) return true;
            if (tag) {
                return type.is(TagKey.create(Registries.ENTITY_TYPE, id));
            }
            return id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        }
    }
}

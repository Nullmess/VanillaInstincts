package fr.vanillainstincts.data;

import net.minecraft.util.registry.Registry;
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
import net.minecraft.util.ResourceLocation;
import net.minecraft.resources.IResourceManager;
import net.minecraft.client.resources.JsonReloadListener;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.profiler.IProfiler;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;

/**
 * Species-aware AnimalEntity Welfare 3.0 settings loaded from datapacks.
 *
 * <p>Files live in {@code data/<namespace>/animal_welfare/*.json}. Profiles
 * only tune bounded local observations; they never reveal unloaded or hidden
 * information.</p>
 */
public final class AnimalWelfareProfileManager
        extends JsonReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile List<WelfareProfile> profiles = fr.vanillainstincts.compat.LegacyJava8.listOf();

    private static final WelfareProfile DEFAULT = new WelfareProfile(
            new ResourceLocation(VanillaInstincts.MOD_ID,
                    "default"), Integer.MIN_VALUE, fr.vanillainstincts.compat.LegacyJava8.listOf(EntityMatcher.any()),
            true, true, true, true, 1.0D, 12_000L, 23_000L,
            58, 72, 320L, 52);

    public AnimalWelfareProfileManager() {
        super(GSON, "animal_welfare");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects,
                         IResourceManager resourceManager,
                         IProfiler profiler) {
        List<WelfareProfile> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            try {
                loaded.add(parse(entry.getKey(), entry.getValue()));
            } catch (RuntimeException exception) {
                VanillaInstincts.LOGGER.warn(
                        "Profil AnimalEntity Welfare ignoré ({}): {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        loaded.sort(Comparator.comparingInt(WelfareProfile::priority).reversed()
                .thenComparing(profile -> profile.id().toString()));
        profiles = fr.vanillainstincts.compat.LegacyJava8.copyList(loaded);
        VanillaInstincts.LOGGER.info(
                "AnimalEntity Welfare 3.0: {} profil(s) datapack chargé(s)",
                profiles.size());
    }

    public static int profileCount() {
        return profiles.size();
    }

    public static WelfareProfile profileFor(AnimalEntity animal) {
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
            return fr.vanillainstincts.compat.LegacyJava8.listOf(EntityMatcher.any());
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
        return fr.vanillainstincts.compat.LegacyJava8.copyList(result);
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

    public static class WelfareProfile {
        private final ResourceLocation id;
        private final int priority;
        private final List<EntityMatcher> animals;
        private final boolean needsShelter;
        private final boolean needsNearbyWater;
        private final boolean needsHerd;
        private final boolean needsRest;
        private final double hotTemperature;
        private final long restStart;
        private final long restEnd;
        private final int stressThreshold;
        private final int recoveryThreshold;
        private final long recoveryTicks;
        private final int breedingMinimumScore;

        public WelfareProfile(ResourceLocation id, int priority, List<EntityMatcher> animals, boolean needsShelter, boolean needsNearbyWater, boolean needsHerd, boolean needsRest, double hotTemperature, long restStart, long restEnd, int stressThreshold, int recoveryThreshold, long recoveryTicks, int breedingMinimumScore) {
            this.id = id;
            this.priority = priority;
            this.animals = animals;
            this.needsShelter = needsShelter;
            this.needsNearbyWater = needsNearbyWater;
            this.needsHerd = needsHerd;
            this.needsRest = needsRest;
            this.hotTemperature = hotTemperature;
            this.restStart = restStart;
            this.restEnd = restEnd;
            this.stressThreshold = stressThreshold;
            this.recoveryThreshold = recoveryThreshold;
            this.recoveryTicks = recoveryTicks;
            this.breedingMinimumScore = breedingMinimumScore;
        }

        public ResourceLocation id() { return this.id; }

        public int priority() { return this.priority; }

        public List<EntityMatcher> animals() { return this.animals; }

        public boolean needsShelter() { return this.needsShelter; }

        public boolean needsNearbyWater() { return this.needsNearbyWater; }

        public boolean needsHerd() { return this.needsHerd; }

        public boolean needsRest() { return this.needsRest; }

        public double hotTemperature() { return this.hotTemperature; }

        public long restStart() { return this.restStart; }

        public long restEnd() { return this.restEnd; }

        public int stressThreshold() { return this.stressThreshold; }

        public int recoveryThreshold() { return this.recoveryThreshold; }

        public long recoveryTicks() { return this.recoveryTicks; }

        public int breedingMinimumScore() { return this.breedingMinimumScore; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof WelfareProfile)) return false;
            WelfareProfile that = (WelfareProfile) other;
            return java.util.Objects.equals(this.id, that.id) && this.priority == that.priority && java.util.Objects.equals(this.animals, that.animals) && this.needsShelter == that.needsShelter && this.needsNearbyWater == that.needsNearbyWater && this.needsHerd == that.needsHerd && this.needsRest == that.needsRest && Double.compare(this.hotTemperature, that.hotTemperature) == 0 && this.restStart == that.restStart && this.restEnd == that.restEnd && this.stressThreshold == that.stressThreshold && this.recoveryThreshold == that.recoveryThreshold && this.recoveryTicks == that.recoveryTicks && this.breedingMinimumScore == that.breedingMinimumScore;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.id, this.priority, this.animals, this.needsShelter, this.needsNearbyWater, this.needsHerd, this.needsRest, this.hotTemperature, this.restStart, this.restEnd, this.stressThreshold, this.recoveryThreshold, this.recoveryTicks, this.breedingMinimumScore); }

        @Override
        public String toString() {
            return "WelfareProfile[" + "id=" + this.id + ", " + "priority=" + this.priority + ", " + "animals=" + this.animals + ", " + "needsShelter=" + this.needsShelter + ", " + "needsNearbyWater=" + this.needsNearbyWater + ", " + "needsHerd=" + this.needsHerd + ", " + "needsRest=" + this.needsRest + ", " + "hotTemperature=" + this.hotTemperature + ", " + "restStart=" + this.restStart + ", " + "restEnd=" + this.restEnd + ", " + "stressThreshold=" + this.stressThreshold + ", " + "recoveryThreshold=" + this.recoveryThreshold + ", " + "recoveryTicks=" + this.recoveryTicks + ", " + "breedingMinimumScore=" + this.breedingMinimumScore + "]";
        }

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

    public static class EntityMatcher {
        private final boolean wildcard;
        private final boolean tag;
        private final ResourceLocation id;

        public EntityMatcher(boolean wildcard, boolean tag, ResourceLocation id) {
            this.wildcard = wildcard;
            this.tag = tag;
            this.id = id;
        }

        public boolean wildcard() { return this.wildcard; }

        public boolean tag() { return this.tag; }

        public ResourceLocation id() { return this.id; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof EntityMatcher)) return false;
            EntityMatcher that = (EntityMatcher) other;
            return this.wildcard == that.wildcard && this.tag == that.tag && java.util.Objects.equals(this.id, that.id);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.wildcard, this.tag, this.id); }

        @Override
        public String toString() {
            return "EntityMatcher[" + "wildcard=" + this.wildcard + ", " + "tag=" + this.tag + ", " + "id=" + this.id + "]";
        }

        static EntityMatcher any() {
            return new EntityMatcher(true, false, null);
        }

        static EntityMatcher parse(String selector) {
            if (selector == null || selector.trim().isEmpty() || "*".equals(selector)) {
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
                return type.is(EntityTypeTags.createOptional(id));
            }
            return id.equals(Registry.ENTITY_TYPE.getKey(type));
        }
    }
}

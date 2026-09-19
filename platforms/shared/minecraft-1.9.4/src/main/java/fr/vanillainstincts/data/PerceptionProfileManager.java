package fr.vanillainstincts.data;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.LegacyRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.PerceptionRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;

/** Data-driven vision, hearing and memory parameters for Perception 2.0. */
public final class PerceptionProfileManager {
    private static final Gson GSON = new GsonBuilder().create();
    private static final PerceptionProfile FALLBACK = new PerceptionProfile(
            40.0D, 1.0D, 1.05D, 0.88D, 0.72D, 0.55D,
            PerceptionRules.BLINDNESS_VISUAL_RANGE,
            0.72D, 0.90D, 1.0D, 1.12D,
            PerceptionRules.LAST_SEEN_MEMORY_TICKS,
            PerceptionRules.LAST_HEARD_MEMORY_TICKS);
    private static volatile List<ProfileRule> rules = fr.vanillainstincts.compat.LegacyJava8.listOf();
    private static volatile double maximumHearingScale = 1.0D;
    private static final Map<ResourceLocation, PerceptionProfile> PROFILE_CACHE =
            new ConcurrentHashMap<>();

    public PerceptionProfileManager() {}

    private void apply(Map<ResourceLocation, JsonObject> objects) {
        List<ProfileRule> loaded = new ArrayList<>();
        double maxHearing = 1.0D;
        for (Map.Entry<ResourceLocation, JsonObject> entry : objects.entrySet()) {
            try {
                ProfileRule rule = parse(entry.getKey(), entry.getValue());
                if (rule == null) continue;
                loaded.add(rule);
                PerceptionProfile profile = rule.profile();
                double maxDifficulty = Math.max(profile.easyDifficultyMultiplier(),
                        Math.max(profile.normalDifficultyMultiplier(),
                                profile.hardDifficultyMultiplier()));
                maxHearing = Math.max(maxHearing,
                        profile.hearingMultiplier() * maxDifficulty);
            } catch (RuntimeException exception) {
                VanillaInstincts.LOGGER.warn(
                        "Profil de perception ignoré ({}): {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        loaded.sort(Comparator.comparingInt(ProfileRule::priority).reversed()
                .thenComparing(rule -> rule.id().toString()));
        rules = fr.vanillainstincts.compat.LegacyJava8.copyList(loaded);
        maximumHearingScale = clamp(maxHearing, 0.25D, 8.0D);
        PROFILE_CACHE.clear();
        VanillaInstincts.LOGGER.info(
                "Perception 2.0: {} profil(s) datapack chargé(s)",
                rules.size());
    }

    public static int profileRuleCount() {
        return rules.size();
    }

    public static PerceptionProfile profileFor(EntityLiving mob) {
        return mob == null ? FALLBACK : profileFor(fr.vanillainstincts.compat.Minecraft110EntityCompat.key(mob));
    }

    public static PerceptionProfile profileFor(ResourceLocation type) {
        if (type == null) return FALLBACK;
        return PROFILE_CACHE.computeIfAbsent(type, ignored -> {
            for (ProfileRule rule : rules) {
                if (rule.matches(type)) return rule.profile();
            }
            return FALLBACK;
        });
    }

    public static double visualRange(EntityLiving observer, EntityLivingBase target,
                                     WorldServer level) {
        if (observer == null || target == null || level == null) return 0.0D;
        PerceptionProfile profile = profileFor(observer);
        double range = profile.visualRange()
                * difficultyMultiplier(profile, level.getDifficulty())
                * RuntimeConfig.snapshot().distanceMultiplier();
        if (isNight(level)) range *= profile.nightVisualMultiplier();
        if (level.isRainingAt(entityBlockPos(observer))) {
            range *= profile.rainVisualMultiplier();
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.isSneaking(target)) {
            range *= profile.sneakingVisualMultiplier();
        }
        if (target.isInvisible()) {
            range *= profile.invisibleVisualMultiplier();
        }
        return clamp(range, 1.0D, 128.0D);
    }

    public static double hearingRadius(EntityLiving observer, WorldServer level,
                                       double baseRadius,
                                       EntityLivingBase noiseSource) {
        if (observer == null || level == null || baseRadius <= 0.0D) {
            return 0.0D;
        }
        PerceptionProfile profile = profileFor(observer);
        double radius = baseRadius * profile.hearingMultiplier()
                * difficultyMultiplier(profile, level.getDifficulty())
                * RuntimeConfig.snapshot().distanceMultiplier();
        if (noiseSource != null && fr.vanillainstincts.compat.Minecraft112Compat.isSneaking(noiseSource)) {
            radius *= profile.sneakingHearingMultiplier();
        }
        if (level.isRainingAt(entityBlockPos(observer))) {
            radius *= 0.92D;
        }
        return clamp(radius, 1.0D, 128.0D);
    }

    public static double maximumNoiseScanRadius(double baseRadius) {
        return clamp(baseRadius * maximumHearingScale
                * RuntimeConfig.snapshot().distanceMultiplier(),
                Math.max(1.0D, baseRadius), 128.0D);
    }

    public static int seenMemoryTicks(EntityLiving mob) {
        return profileFor(mob).lastSeenTicks();
    }

    public static int heardMemoryTicks(EntityLiving mob) {
        return profileFor(mob).lastHeardTicks();
    }

    public static double blindnessRange(EntityLiving mob) {
        return profileFor(mob).blindnessRange();
    }

    private static boolean isNight(WorldServer level) {
        long time = Math.floorMod(level.getWorldTime(), 24_000L);
        return time >= 13_000L && time <= 23_000L;
    }

    private static double difficultyMultiplier(PerceptionProfile profile,
                                               EnumDifficulty difficulty) {
        if (difficulty == null) return 1.0D;
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((difficulty)) { case PEACEFUL:  return profile.easyDifficultyMultiplier() * 0.85D; case EASY:  return profile.easyDifficultyMultiplier(); case NORMAL:  return profile.normalDifficultyMultiplier(); case HARD:  return profile.hardDifficultyMultiplier();  default: throw new AssertionError("Unexpected switch value"); } });
    }

    private static ProfileRule parse(ResourceLocation id, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("racine JSON attendue");
        }
        JsonObject root = element.getAsJsonObject();
        int priority = integer(root, "priority", 0);
        List<EntityMatcher> entities = entityMatchers(root.get("entities"));
        PerceptionProfile profile = new PerceptionProfile(
                number(root, "visual_range", FALLBACK.visualRange()),
                number(root, "hearing_multiplier", FALLBACK.hearingMultiplier()),
                number(root, "night_visual_multiplier",
                        FALLBACK.nightVisualMultiplier()),
                number(root, "rain_visual_multiplier",
                        FALLBACK.rainVisualMultiplier()),
                number(root, "sneaking_visual_multiplier",
                        FALLBACK.sneakingVisualMultiplier()),
                number(root, "invisible_visual_multiplier",
                        FALLBACK.invisibleVisualMultiplier()),
                number(root, "blindness_range", FALLBACK.blindnessRange()),
                number(root, "sneaking_hearing_multiplier",
                        FALLBACK.sneakingHearingMultiplier()),
                number(root, "easy_multiplier",
                        FALLBACK.easyDifficultyMultiplier()),
                number(root, "normal_multiplier",
                        FALLBACK.normalDifficultyMultiplier()),
                number(root, "hard_multiplier",
                        FALLBACK.hardDifficultyMultiplier()),
                integer(root, "last_seen_ticks", FALLBACK.lastSeenTicks()),
                integer(root, "last_heard_ticks", FALLBACK.lastHeardTicks())
        ).validated();
        return new ProfileRule(id, priority, entities, profile);
    }

    private static List<EntityMatcher> entityMatchers(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return fr.vanillainstincts.compat.LegacyJava8.listOf(EntityMatcher.any());
        }
        Set<String> selectors = new HashSet<>();
        if (element.isJsonPrimitive()) {
            selectors.add(element.getAsString());
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> selectors.add(
                    value.getAsString()));
        } else {
            throw new IllegalArgumentException(
                    "entities doit être une chaîne ou une liste");
        }
        List<EntityMatcher> result = new ArrayList<>();
        for (String selector : selectors) {
            result.add(EntityMatcher.parse(selector));
        }
        return fr.vanillainstincts.compat.LegacyJava8.copyList(result);
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
        return Math.max(min, Math.min(max, value));
    }

    public static class PerceptionProfile {
        private final double visualRange;
        private final double hearingMultiplier;
        private final double nightVisualMultiplier;
        private final double rainVisualMultiplier;
        private final double sneakingVisualMultiplier;
        private final double invisibleVisualMultiplier;
        private final double blindnessRange;
        private final double sneakingHearingMultiplier;
        private final double easyDifficultyMultiplier;
        private final double normalDifficultyMultiplier;
        private final double hardDifficultyMultiplier;
        private final int lastSeenTicks;
        private final int lastHeardTicks;

        public PerceptionProfile(double visualRange, double hearingMultiplier, double nightVisualMultiplier, double rainVisualMultiplier, double sneakingVisualMultiplier, double invisibleVisualMultiplier, double blindnessRange, double sneakingHearingMultiplier, double easyDifficultyMultiplier, double normalDifficultyMultiplier, double hardDifficultyMultiplier, int lastSeenTicks, int lastHeardTicks) {
            this.visualRange = visualRange;
            this.hearingMultiplier = hearingMultiplier;
            this.nightVisualMultiplier = nightVisualMultiplier;
            this.rainVisualMultiplier = rainVisualMultiplier;
            this.sneakingVisualMultiplier = sneakingVisualMultiplier;
            this.invisibleVisualMultiplier = invisibleVisualMultiplier;
            this.blindnessRange = blindnessRange;
            this.sneakingHearingMultiplier = sneakingHearingMultiplier;
            this.easyDifficultyMultiplier = easyDifficultyMultiplier;
            this.normalDifficultyMultiplier = normalDifficultyMultiplier;
            this.hardDifficultyMultiplier = hardDifficultyMultiplier;
            this.lastSeenTicks = lastSeenTicks;
            this.lastHeardTicks = lastHeardTicks;
        }

        public double visualRange() { return this.visualRange; }

        public double hearingMultiplier() { return this.hearingMultiplier; }

        public double nightVisualMultiplier() { return this.nightVisualMultiplier; }

        public double rainVisualMultiplier() { return this.rainVisualMultiplier; }

        public double sneakingVisualMultiplier() { return this.sneakingVisualMultiplier; }

        public double invisibleVisualMultiplier() { return this.invisibleVisualMultiplier; }

        public double blindnessRange() { return this.blindnessRange; }

        public double sneakingHearingMultiplier() { return this.sneakingHearingMultiplier; }

        public double easyDifficultyMultiplier() { return this.easyDifficultyMultiplier; }

        public double normalDifficultyMultiplier() { return this.normalDifficultyMultiplier; }

        public double hardDifficultyMultiplier() { return this.hardDifficultyMultiplier; }

        public int lastSeenTicks() { return this.lastSeenTicks; }

        public int lastHeardTicks() { return this.lastHeardTicks; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PerceptionProfile)) return false;
            PerceptionProfile that = (PerceptionProfile) other;
            return Double.compare(this.visualRange, that.visualRange) == 0 && Double.compare(this.hearingMultiplier, that.hearingMultiplier) == 0 && Double.compare(this.nightVisualMultiplier, that.nightVisualMultiplier) == 0 && Double.compare(this.rainVisualMultiplier, that.rainVisualMultiplier) == 0 && Double.compare(this.sneakingVisualMultiplier, that.sneakingVisualMultiplier) == 0 && Double.compare(this.invisibleVisualMultiplier, that.invisibleVisualMultiplier) == 0 && Double.compare(this.blindnessRange, that.blindnessRange) == 0 && Double.compare(this.sneakingHearingMultiplier, that.sneakingHearingMultiplier) == 0 && Double.compare(this.easyDifficultyMultiplier, that.easyDifficultyMultiplier) == 0 && Double.compare(this.normalDifficultyMultiplier, that.normalDifficultyMultiplier) == 0 && Double.compare(this.hardDifficultyMultiplier, that.hardDifficultyMultiplier) == 0 && this.lastSeenTicks == that.lastSeenTicks && this.lastHeardTicks == that.lastHeardTicks;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.visualRange, this.hearingMultiplier, this.nightVisualMultiplier, this.rainVisualMultiplier, this.sneakingVisualMultiplier, this.invisibleVisualMultiplier, this.blindnessRange, this.sneakingHearingMultiplier, this.easyDifficultyMultiplier, this.normalDifficultyMultiplier, this.hardDifficultyMultiplier, this.lastSeenTicks, this.lastHeardTicks); }

        @Override
        public String toString() {
            return "PerceptionProfile[" + "visualRange=" + this.visualRange + ", " + "hearingMultiplier=" + this.hearingMultiplier + ", " + "nightVisualMultiplier=" + this.nightVisualMultiplier + ", " + "rainVisualMultiplier=" + this.rainVisualMultiplier + ", " + "sneakingVisualMultiplier=" + this.sneakingVisualMultiplier + ", " + "invisibleVisualMultiplier=" + this.invisibleVisualMultiplier + ", " + "blindnessRange=" + this.blindnessRange + ", " + "sneakingHearingMultiplier=" + this.sneakingHearingMultiplier + ", " + "easyDifficultyMultiplier=" + this.easyDifficultyMultiplier + ", " + "normalDifficultyMultiplier=" + this.normalDifficultyMultiplier + ", " + "hardDifficultyMultiplier=" + this.hardDifficultyMultiplier + ", " + "lastSeenTicks=" + this.lastSeenTicks + ", " + "lastHeardTicks=" + this.lastHeardTicks + "]";
        }

        PerceptionProfile validated() {
            return new PerceptionProfile(
                    clamp(visualRange, 2.0D, 128.0D),
                    clamp(hearingMultiplier, 0.1D, 4.0D),
                    clamp(nightVisualMultiplier, 0.1D, 3.0D),
                    clamp(rainVisualMultiplier, 0.1D, 2.0D),
                    clamp(sneakingVisualMultiplier, 0.1D, 1.0D),
                    clamp(invisibleVisualMultiplier, 0.05D, 1.0D),
                    clamp(blindnessRange, 1.0D, 16.0D),
                    clamp(sneakingHearingMultiplier, 0.1D, 1.0D),
                    clamp(easyDifficultyMultiplier, 0.25D, 2.0D),
                    clamp(normalDifficultyMultiplier, 0.25D, 2.0D),
                    clamp(hardDifficultyMultiplier, 0.25D, 2.0D),
                    Math.max(1, Math.min(2_400, lastSeenTicks)),
                    Math.max(1, Math.min(2_400, lastHeardTicks)));
        }
    }

    private static class ProfileRule {
        private final ResourceLocation id;
        private final int priority;
        private final List<EntityMatcher> entities;
        private final PerceptionProfile profile;

        public ProfileRule(ResourceLocation id, int priority, List<EntityMatcher> entities, PerceptionProfile profile) {
            this.id = id;
            this.priority = priority;
            this.entities = entities;
            this.profile = profile;
        }

        public ResourceLocation id() { return this.id; }

        public int priority() { return this.priority; }

        public List<EntityMatcher> entities() { return this.entities; }

        public PerceptionProfile profile() { return this.profile; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ProfileRule)) return false;
            ProfileRule that = (ProfileRule) other;
            return java.util.Objects.equals(this.id, that.id) && this.priority == that.priority && java.util.Objects.equals(this.entities, that.entities) && java.util.Objects.equals(this.profile, that.profile);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.id, this.priority, this.entities, this.profile); }

        @Override
        public String toString() {
            return "ProfileRule[" + "id=" + this.id + ", " + "priority=" + this.priority + ", " + "entities=" + this.entities + ", " + "profile=" + this.profile + "]";
        }

        boolean matches(ResourceLocation type) {
            for (EntityMatcher matcher : entities) {
                if (matcher.matches(type)) return true;
            }
            return false;
        }
    }

    private static class EntityMatcher {
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
            String rawId = tag ? selector.substring(1) : selector;
            ResourceLocation id = fr.vanillainstincts.compat.LegacyResourceLocation.tryParse(rawId);
            if (id == null) {
                throw new IllegalArgumentException(
                        "identifiant d'entité/tag invalide: " + selector);
            }
            return new EntityMatcher(false, tag, id);
        }

        boolean matches(ResourceLocation type) {
            if (wildcard) return true;
            if (tag) {
                return fr.vanillainstincts.compat.Minecraft115TagCompat.entityTypeTag(id).contains(type);
            }
            return id.equals(type);
        }
    }
}

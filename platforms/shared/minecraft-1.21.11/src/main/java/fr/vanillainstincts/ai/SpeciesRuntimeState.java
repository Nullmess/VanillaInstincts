package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
/**
 * État persistant propre aux creepers et araignées.
 *
 * <p>Ces données sont séparées de la mémoire sensorielle et de l'état de
 * combat zombies/squelettes afin que chaque sous-système reste lisible et
 * puisse évoluer indépendamment.</p>
 */
public final class SpeciesRuntimeState {
    private static final String ROOT_KEY = VanillaInstincts.MOD_ID + "_species";
    public static final int DATA_VERSION =
            PersistentDataVersions.SPECIES_RUNTIME;

    private int creeperWatchedTicks;
    private int creeperUnwatchedTicks;
    private boolean creeperHasMovementSample;
    private double creeperMovementX;
    private double creeperMovementY;
    private double creeperMovementZ;
    private long creeperMovementSampleAt;
    private int creeperStalledSamples;
    private long creeperAmbushReadyAt;
    private long creeperLeapReadyAt;
    private long creeperPassageReadyAt;

    private SpiderSurfaceMode spiderSurfaceMode = SpiderSurfaceMode.GROUND;
    private long spiderSurfaceSince;
    private long spiderSurfaceReadyAt;
    private long spiderWebReadyAt;
    private long spiderLeapReadyAt;
    private long spiderLastContactAt;
    private long spiderMountReadyAt;

    private boolean dirty;

    public static SpeciesRuntimeState load(Mob mob) {
        SpeciesRuntimeState state = new SpeciesRuntimeState();
        CompoundTag persistent = mob.getPersistentData();
        if (!fr.vanillainstincts.persistence.NbtCompat.contains(persistent, ROOT_KEY)) {
            return state;
        }

        CompoundTag tag = fr.vanillainstincts.persistence.NbtCompat.getCompound(persistent, ROOT_KEY);
        state.creeperWatchedTicks = bounded(fr.vanillainstincts.persistence.NbtCompat.getInt(tag, "creeper_watched_ticks"), 0, 400);
        state.creeperUnwatchedTicks = bounded(fr.vanillainstincts.persistence.NbtCompat.getInt(tag, "creeper_unwatched_ticks"), 0, 400);
        state.creeperHasMovementSample = fr.vanillainstincts.persistence.NbtCompat.getBoolean(tag, "creeper_has_movement_sample");
        state.creeperMovementX = fr.vanillainstincts.persistence.NbtCompat.getDouble(tag, "creeper_movement_x");
        state.creeperMovementY = fr.vanillainstincts.persistence.NbtCompat.getDouble(tag, "creeper_movement_y");
        state.creeperMovementZ = fr.vanillainstincts.persistence.NbtCompat.getDouble(tag, "creeper_movement_z");
        state.creeperMovementSampleAt = Math.max(0L, fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "creeper_movement_sample_at"));
        state.creeperStalledSamples = bounded(fr.vanillainstincts.persistence.NbtCompat.getInt(tag, "creeper_stalled_samples"), 0, 100);
        state.creeperAmbushReadyAt = Math.max(0L, fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "creeper_ambush_ready_at"));
        state.creeperLeapReadyAt = Math.max(0L, fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "creeper_leap_ready_at"));
        state.creeperPassageReadyAt = Math.max(0L, fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "creeper_passage_ready_at"));

        state.spiderSurfaceMode = readEnum(fr.vanillainstincts.persistence.NbtCompat.getString(tag, "spider_surface_mode"),
                SpiderSurfaceMode.GROUND, SpiderSurfaceMode.class);
        state.spiderSurfaceSince = Math.max(0L, fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "spider_surface_since"));
        state.spiderSurfaceReadyAt = Math.max(0L, fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "spider_surface_ready_at"));
        state.spiderWebReadyAt = Math.max(0L, fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "spider_web_ready_at"));
        state.spiderLeapReadyAt = Math.max(0L, fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "spider_leap_ready_at"));
        state.spiderLastContactAt = Math.max(0L, fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "spider_last_contact_at"));
        state.spiderMountReadyAt = Math.max(0L, fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "spider_mount_ready_at"));

        state.dirty = NbtSchema.requiresRewrite(tag, DATA_VERSION)
                || NbtSchema.hasNegativeLong(tag,
                "creeper_ambush_ready_at", "creeper_leap_ready_at",
                "creeper_passage_ready_at", "creeper_movement_sample_at",
                "spider_surface_since", "spider_surface_ready_at",
                "spider_web_ready_at", "spider_leap_ready_at",
                "spider_last_contact_at", "spider_mount_ready_at")
                || NbtSchema.hasNegativeInt(tag, "creeper_watched_ticks",
                "creeper_unwatched_ticks", "creeper_stalled_samples");
        state.sanitize(mob.level().getGameTime());
        return state;
    }

    public void save(Mob mob) {
        if (!dirty) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.putInt("creeper_watched_ticks", creeperWatchedTicks);
        tag.putInt("creeper_unwatched_ticks", creeperUnwatchedTicks);
        tag.putBoolean("creeper_has_movement_sample", creeperHasMovementSample);
        tag.putDouble("creeper_movement_x", creeperMovementX);
        tag.putDouble("creeper_movement_y", creeperMovementY);
        tag.putDouble("creeper_movement_z", creeperMovementZ);
        tag.putLong("creeper_movement_sample_at", creeperMovementSampleAt);
        tag.putInt("creeper_stalled_samples", creeperStalledSamples);
        tag.putLong("creeper_ambush_ready_at", creeperAmbushReadyAt);
        tag.putLong("creeper_leap_ready_at", creeperLeapReadyAt);
        tag.putLong("creeper_passage_ready_at", creeperPassageReadyAt);

        tag.putString("spider_surface_mode", spiderSurfaceMode.name());
        tag.putLong("spider_surface_since", spiderSurfaceSince);
        tag.putLong("spider_surface_ready_at", spiderSurfaceReadyAt);
        tag.putLong("spider_web_ready_at", spiderWebReadyAt);
        tag.putLong("spider_leap_ready_at", spiderLeapReadyAt);
        tag.putLong("spider_last_contact_at", spiderLastContactAt);
        tag.putLong("spider_mount_ready_at", spiderMountReadyAt);

        mob.getPersistentData().put(ROOT_KEY, tag);
        dirty = false;
    }

    public void recordCreeperGaze(boolean watched) {
        if (watched) {
            creeperWatchedTicks = Math.min(400, creeperWatchedTicks + 1);
            creeperUnwatchedTicks = Math.max(0, creeperUnwatchedTicks - 2);
        } else {
            creeperUnwatchedTicks = Math.min(400, creeperUnwatchedTicks + 1);
            creeperWatchedTicks = Math.max(0, creeperWatchedTicks - 2);
        }
        // Historique volontairement transitoire : il ne justifie pas une
        // réécriture NBT à chaque cycle de décision.
    }

    public void sampleCreeperMovement(Vec3 position, long gameTime,
                                      boolean expectedToMove) {
        if (position == null || !finite(position)) {
            return;
        }
        if (!creeperHasMovementSample) {
            creeperHasMovementSample = true;
            setCreeperMovementSample(position, gameTime);
            return;
        }
        if (gameTime - creeperMovementSampleAt < 4L) {
            return;
        }

        Vec3 previous = new Vec3(creeperMovementX, creeperMovementY,
                creeperMovementZ);
        if (expectedToMove && previous.distanceToSqr(position) < 0.035D) {
            creeperStalledSamples = Math.min(100, creeperStalledSamples + 1);
        } else {
            creeperStalledSamples = Math.max(0, creeperStalledSamples - 2);
        }
        setCreeperMovementSample(position, gameTime);
    }

    public void clearCreeperStall() {
        if (creeperStalledSamples != 0) {
            creeperStalledSamples = 0;
            dirty = true;
        }
    }

    public void setCreeperAmbushCooldown(long gameTime, int ticks) {
        creeperAmbushReadyAt = gameTime + Math.max(0, ticks);
        dirty = true;
    }

    public void setCreeperLeapCooldown(long gameTime, int ticks) {
        creeperLeapReadyAt = gameTime + Math.max(0, ticks);
        dirty = true;
    }

    public void setCreeperPassageCooldown(long gameTime, int ticks) {
        creeperPassageReadyAt = gameTime + Math.max(0, ticks);
        dirty = true;
    }

    public void setSpiderSurfaceMode(SpiderSurfaceMode mode, long gameTime) {
        SpiderSurfaceMode normalized = mode == null
                ? SpiderSurfaceMode.GROUND : mode;
        if (spiderSurfaceMode != normalized) {
            spiderSurfaceMode = normalized;
            spiderSurfaceSince = gameTime;
            dirty = true;
        }
    }

    public void markSpiderContact(long gameTime) {
        spiderLastContactAt = gameTime;
    }

    public void setSpiderSurfaceCooldown(long gameTime, int ticks) {
        spiderSurfaceReadyAt = gameTime + Math.max(0, ticks);
        dirty = true;
    }

    public void setSpiderWebCooldown(long gameTime, int ticks) {
        spiderWebReadyAt = gameTime + Math.max(0, ticks);
        dirty = true;
    }

    public void setSpiderLeapCooldown(long gameTime, int ticks) {
        spiderLeapReadyAt = gameTime + Math.max(0, ticks);
        dirty = true;
    }

    public void setSpiderMountCooldown(long gameTime, int ticks) {
        spiderMountReadyAt = gameTime + Math.max(0, ticks);
        dirty = true;
    }

    private void setCreeperMovementSample(Vec3 position, long gameTime) {
        creeperMovementX = position.x;
        creeperMovementY = position.y;
        creeperMovementZ = position.z;
        creeperMovementSampleAt = gameTime;
    }

    private void sanitize(long gameTime) {
        if (!finite(new Vec3(creeperMovementX, creeperMovementY,
                creeperMovementZ))) {
            creeperHasMovementSample = false;
            creeperMovementX = 0.0D;
            creeperMovementY = 0.0D;
            creeperMovementZ = 0.0D;
            creeperMovementSampleAt = gameTime;
            creeperStalledSamples = 0;
            dirty = true;
        }
        if (spiderSurfaceSince > gameTime + 20L) {
            spiderSurfaceSince = gameTime;
            dirty = true;
        }
    }

    public int creeperWatchedTicks() { return creeperWatchedTicks; }
    public int creeperUnwatchedTicks() { return creeperUnwatchedTicks; }
    public int creeperStalledSamples() { return creeperStalledSamples; }
    public boolean creeperAmbushReady(long gameTime) {
        return gameTime >= creeperAmbushReadyAt;
    }
    public boolean creeperLeapReady(long gameTime) {
        return gameTime >= creeperLeapReadyAt;
    }
    public boolean creeperPassageReady(long gameTime) {
        return gameTime >= creeperPassageReadyAt;
    }
    public SpiderSurfaceMode spiderSurfaceMode() { return spiderSurfaceMode; }
    public long spiderSurfaceSince() { return spiderSurfaceSince; }
    public long spiderLastContactAt() { return spiderLastContactAt; }
    public boolean spiderSurfaceReady(long gameTime) {
        return gameTime >= spiderSurfaceReadyAt;
    }
    public boolean spiderWebReady(long gameTime) {
        return gameTime >= spiderWebReadyAt;
    }
    public boolean spiderLeapReady(long gameTime) {
        return gameTime >= spiderLeapReadyAt;
    }
    public boolean spiderMountReady(long gameTime) {
        return gameTime >= spiderMountReadyAt;
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static <E extends Enum<E>> E readEnum(String name, E fallback,
                                                   Class<E> type) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

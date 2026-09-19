package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.EntityLiving;
import fr.vanillainstincts.compat.Vec3;
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

    public static SpeciesRuntimeState load(EntityLiving mob) {
        SpeciesRuntimeState state = new SpeciesRuntimeState();
        NBTTagCompound persistent = mob.getEntityData();
        if (!persistent.hasKey(ROOT_KEY)) {
            return state;
        }

        NBTTagCompound tag = persistent.getCompoundTag(ROOT_KEY);
        state.creeperWatchedTicks = bounded(tag.getInteger("creeper_watched_ticks"), 0, 400);
        state.creeperUnwatchedTicks = bounded(tag.getInteger("creeper_unwatched_ticks"), 0, 400);
        state.creeperHasMovementSample = tag.getBoolean("creeper_has_movement_sample");
        state.creeperMovementX = tag.getDouble("creeper_movement_x");
        state.creeperMovementY = tag.getDouble("creeper_movement_y");
        state.creeperMovementZ = tag.getDouble("creeper_movement_z");
        state.creeperMovementSampleAt = Math.max(0L, tag.getLong("creeper_movement_sample_at"));
        state.creeperStalledSamples = bounded(tag.getInteger("creeper_stalled_samples"), 0, 100);
        state.creeperAmbushReadyAt = Math.max(0L, tag.getLong("creeper_ambush_ready_at"));
        state.creeperLeapReadyAt = Math.max(0L, tag.getLong("creeper_leap_ready_at"));
        state.creeperPassageReadyAt = Math.max(0L, tag.getLong("creeper_passage_ready_at"));

        state.spiderSurfaceMode = readEnum(tag.getString("spider_surface_mode"),
                SpiderSurfaceMode.GROUND, SpiderSurfaceMode.class);
        state.spiderSurfaceSince = Math.max(0L, tag.getLong("spider_surface_since"));
        state.spiderSurfaceReadyAt = Math.max(0L, tag.getLong("spider_surface_ready_at"));
        state.spiderWebReadyAt = Math.max(0L, tag.getLong("spider_web_ready_at"));
        state.spiderLeapReadyAt = Math.max(0L, tag.getLong("spider_leap_ready_at"));
        state.spiderLastContactAt = Math.max(0L, tag.getLong("spider_last_contact_at"));
        state.spiderMountReadyAt = Math.max(0L, tag.getLong("spider_mount_ready_at"));

        state.dirty = NbtSchema.requiresRewrite(tag, DATA_VERSION)
                || NbtSchema.hasNegativeLong(tag,
                "creeper_ambush_ready_at", "creeper_leap_ready_at",
                "creeper_passage_ready_at", "creeper_movement_sample_at",
                "spider_surface_since", "spider_surface_ready_at",
                "spider_web_ready_at", "spider_leap_ready_at",
                "spider_last_contact_at", "spider_mount_ready_at")
                || NbtSchema.hasNegativeInt(tag, "creeper_watched_ticks",
                "creeper_unwatched_ticks", "creeper_stalled_samples");
        state.sanitize(mob.worldObj.getTotalWorldTime());
        return state;
    }

    public void save(EntityLiving mob) {
        if (!dirty) {
            return;
        }
        NBTTagCompound tag = new NBTTagCompound();
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.setInteger("creeper_watched_ticks", creeperWatchedTicks);
        tag.setInteger("creeper_unwatched_ticks", creeperUnwatchedTicks);
        tag.setBoolean("creeper_has_movement_sample", creeperHasMovementSample);
        tag.setDouble("creeper_movement_x", creeperMovementX);
        tag.setDouble("creeper_movement_y", creeperMovementY);
        tag.setDouble("creeper_movement_z", creeperMovementZ);
        tag.setLong("creeper_movement_sample_at", creeperMovementSampleAt);
        tag.setInteger("creeper_stalled_samples", creeperStalledSamples);
        tag.setLong("creeper_ambush_ready_at", creeperAmbushReadyAt);
        tag.setLong("creeper_leap_ready_at", creeperLeapReadyAt);
        tag.setLong("creeper_passage_ready_at", creeperPassageReadyAt);

        tag.setString("spider_surface_mode", spiderSurfaceMode.name());
        tag.setLong("spider_surface_since", spiderSurfaceSince);
        tag.setLong("spider_surface_ready_at", spiderSurfaceReadyAt);
        tag.setLong("spider_web_ready_at", spiderWebReadyAt);
        tag.setLong("spider_leap_ready_at", spiderLeapReadyAt);
        tag.setLong("spider_last_contact_at", spiderLastContactAt);
        tag.setLong("spider_mount_ready_at", spiderMountReadyAt);

        mob.getEntityData().setTag(ROOT_KEY, tag);
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
        if (expectedToMove && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(previous, position) < 0.035D) {
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
        creeperMovementX = position.xCoord;
        creeperMovementY = position.yCoord;
        creeperMovementZ = position.zCoord;
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
        return value != null && Double.isFinite(value.xCoord)
                && Double.isFinite(value.yCoord) && Double.isFinite(value.zCoord);
    }

    private static <E extends Enum<E>> E readEnum(String name, E fallback,
                                                   Class<E> type) {
        if (name == null || name.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

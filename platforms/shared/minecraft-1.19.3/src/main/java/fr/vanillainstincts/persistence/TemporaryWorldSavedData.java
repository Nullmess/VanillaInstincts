package fr.vanillainstincts.persistence;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import fr.vanillainstincts.core.rules.GolemRules;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persiste les blocs temporaires par dimension.
 *
 * <p>Un bloc n'est retiré que si le chunk est chargé et si son état correspond
 * encore à celui placé par le mod. Une modification du joueur est conservée.</p>
 */
public final class TemporaryWorldSavedData extends SavedData {
    private static final String DATA_NAME = VanillaInstincts.MOD_ID
            + "_temporary_world";

    private final Map<BlockPos, Long> webs = new HashMap<>();
    private final Map<BlockPos, ScaffoldEntry> scaffolds = new HashMap<>();
    private long lastWebCleanupAt = Long.MIN_VALUE / 2L;
    private long lastScaffoldCleanupAt = Long.MIN_VALUE / 2L;

    public TemporaryWorldSavedData() {
    }

    public static TemporaryWorldSavedData get(ServerLevel level) {
        if (level == null) {
            return new TemporaryWorldSavedData();
        }
        return level.getDataStorage().computeIfAbsent(TemporaryWorldSavedData::load, TemporaryWorldSavedData::new, DATA_NAME);
    }

    public static TemporaryWorldSavedData load(CompoundTag tag) {
        TemporaryWorldSavedData data = new TemporaryWorldSavedData();
        if (tag == null) {
            return data;
        }

        ListTag webEntries = tag.getList("webs", Tag.TAG_COMPOUND);
        for (int index = 0; index < webEntries.size(); index++) {
            CompoundTag entry = webEntries.getCompound(index);
            if (!entry.contains("pos", Tag.TAG_LONG)) {
                continue;
            }
            data.webs.put(BlockPos.of(entry.getLong("pos")),
                    Math.max(1L, entry.getLong("expires_at")));
        }

        HolderGetter<Block> blockLookup = BuiltInRegistries.BLOCK.asLookup();
        ListTag scaffoldEntries = tag.getList("scaffolds",
                Tag.TAG_COMPOUND);
        for (int index = 0; index < scaffoldEntries.size(); index++) {
            CompoundTag entry = scaffoldEntries.getCompound(index);
            if (!entry.contains("pos", Tag.TAG_LONG)
                    || !entry.contains("state", Tag.TAG_COMPOUND)) {
                continue;
            }
            BlockState state = NbtUtils.readBlockState(blockLookup,
                    entry.getCompound("state"));
            if (state.isAir()) {
                continue;
            }
            UUID owner = entry.hasUUID("owner")
                    ? entry.getUUID("owner") : null;
            data.scaffolds.put(BlockPos.of(entry.getLong("pos")),
                    new ScaffoldEntry(state,
                            Math.max(1L, entry.getLong("expires_at")), owner));
        }

        int storedVersion = NbtSchema.readVersion(tag);
        if (storedVersion != PersistentDataVersions.TEMPORARY_WORLD) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        NbtSchema.writeVersion(tag,
                PersistentDataVersions.TEMPORARY_WORLD);

        ListTag webEntries = new ListTag();
        for (Map.Entry<BlockPos, Long> entry : sortedWebs()) {
            CompoundTag saved = new CompoundTag();
            saved.putLong("pos", entry.getKey().asLong());
            saved.putLong("expires_at", entry.getValue());
            webEntries.add(saved);
        }
        tag.put("webs", webEntries);

        ListTag scaffoldEntries = new ListTag();
        for (Map.Entry<BlockPos, ScaffoldEntry> entry : sortedScaffolds()) {
            CompoundTag saved = new CompoundTag();
            saved.putLong("pos", entry.getKey().asLong());
            saved.put("state", NbtUtils.writeBlockState(
                    entry.getValue().state()));
            saved.putLong("expires_at", entry.getValue().expiresAt());
            if (entry.getValue().ownerId() != null) {
                saved.putUUID("owner", entry.getValue().ownerId());
            }
            scaffoldEntries.add(saved);
        }
        tag.put("scaffolds", scaffoldEntries);
        return tag;
    }

    public synchronized boolean registerWeb(BlockPos pos, long expiresAt) {
        if (pos == null) {
            return false;
        }
        if (webs.size() >= SpiderRules.MAX_TEMPORARY_WEBS_PER_LEVEL
                && !webs.containsKey(pos)) {
            return false;
        }
        Long previous = webs.put(pos.immutable(), Math.max(1L, expiresAt));
        if (previous == null || previous.longValue() != Math.max(1L,
                expiresAt)) {
            setDirty();
        }
        return true;
    }

    public synchronized boolean canRegisterScaffold(BlockPos pos) {
        return pos != null && (scaffolds.containsKey(pos)
                || scaffolds.size()
                < GolemRules.MAX_TEMPORARY_GOLEM_BLOCKS_PER_LEVEL);
    }

    public synchronized boolean registerScaffold(BlockPos pos,
                                                  BlockState state,
                                                  long expiresAt,
                                                  UUID ownerId) {
        if (pos == null || state == null || state.isAir()
                || !canRegisterScaffold(pos)) {
            return false;
        }
        ScaffoldEntry replacement = new ScaffoldEntry(state,
                Math.max(1L, expiresAt), ownerId);
        ScaffoldEntry previous = scaffolds.put(pos.immutable(), replacement);
        if (!replacement.equals(previous)) {
            setDirty();
        }
        return true;
    }

    public synchronized void tickWebs(ServerLevel level, long gameTime) {
        if (level == null || gameTime - lastWebCleanupAt
                < SpiderRules.TEMPORARY_WEB_CLEANUP_INTERVAL_TICKS) {
            return;
        }
        lastWebCleanupAt = gameTime;
        boolean changed = webs.entrySet().removeIf(entry ->
                reconcileWeb(level, entry.getKey(), entry.getValue(),
                        gameTime));
        if (changed) {
            setDirty();
        }
    }

    public synchronized void tickScaffolds(ServerLevel level,
                                            long gameTime) {
        if (level == null || gameTime - lastScaffoldCleanupAt
                < GolemRules.TEMPORARY_GOLEM_BLOCK_CLEANUP_TICKS) {
            return;
        }
        lastScaffoldCleanupAt = gameTime;
        boolean changed = scaffolds.entrySet().removeIf(entry ->
                reconcileScaffold(level, entry.getKey(), entry.getValue(),
                        gameTime));
        if (changed) {
            setDirty();
        }
    }

    private static boolean reconcileWeb(ServerLevel level, BlockPos pos,
                                        long expiresAt, long gameTime) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        if (!current.is(Blocks.COBWEB)) {
            return true;
        }
        if (gameTime < expiresAt) {
            return false;
        }
        return WorldPermissionService.setBlock(level, null, pos,
                Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL,
                WorldActionType.TEMPORARY_CLEANUP);
    }

    private static boolean reconcileScaffold(ServerLevel level,
                                             BlockPos pos,
                                             ScaffoldEntry entry,
                                             long gameTime) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        if (!current.equals(entry.state())) {
            return true;
        }
        if (gameTime < entry.expiresAt()) {
            return false;
        }
        return WorldPermissionService.setBlock(level, null, pos,
                Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL,
                WorldActionType.TEMPORARY_CLEANUP);
    }

    public synchronized int countWebsNear(BlockPos center, double radius) {
        if (center == null || radius < 0.0D) {
            return 0;
        }
        double radiusSqr = radius * radius;
        int count = 0;
        for (BlockPos pos : webs.keySet()) {
            if (pos.distSqr(center) <= radiusSqr) {
                count++;
            }
        }
        return count;
    }

    public synchronized int webCount() {
        return webs.size();
    }

    public synchronized int scaffoldCount() {
        return scaffolds.size();
    }

    public synchronized boolean isTrackedScaffold(ServerLevel level,
                                                   BlockPos pos) {
        ScaffoldEntry entry = pos == null ? null : scaffolds.get(pos);
        return entry != null && level != null && level.isLoaded(pos)
                && level.getBlockState(pos).equals(entry.state());
    }

    public synchronized boolean isTrackedScaffoldBy(ServerLevel level,
                                                     BlockPos pos,
                                                     UUID ownerId) {
        ScaffoldEntry entry = pos == null ? null : scaffolds.get(pos);
        return entry != null && ownerId != null
                && ownerId.equals(entry.ownerId())
                && level != null && level.isLoaded(pos)
                && level.getBlockState(pos).equals(entry.state());
    }

    public synchronized boolean hasScaffoldsByOwner(UUID ownerId) {
        return ownerId != null && scaffolds.values().stream()
                .anyMatch(entry -> ownerId.equals(entry.ownerId()));
    }

    public synchronized int removeScaffoldsByOwner(ServerLevel level,
                                                   UUID ownerId) {
        if (level == null || ownerId == null) {
            return 0;
        }
        int[] removed = {0};
        boolean changed = scaffolds.entrySet().removeIf(entry -> {
            if (!ownerId.equals(entry.getValue().ownerId())) {
                return false;
            }
            BlockPos pos = entry.getKey();
            if (level.isLoaded(pos)
                    && level.getBlockState(pos)
                    .equals(entry.getValue().state())) {
                if (!WorldPermissionService.setBlock(level, null, pos,
                        Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL,
                        WorldActionType.TEMPORARY_CLEANUP)) {
                    return false;
                }
                removed[0]++;
            }
            return true;
        });
        if (changed) {
            setDirty();
        }
        return removed[0];
    }

    public synchronized boolean removeScaffold(ServerLevel level,
                                                BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        ScaffoldEntry entry = scaffolds.get(pos);
        if (entry == null || !level.isLoaded(pos)
                || !level.getBlockState(pos).equals(entry.state())) {
            return false;
        }
        if (!WorldPermissionService.setBlock(level, null, pos,
                Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL,
                WorldActionType.TEMPORARY_CLEANUP)) {
            return false;
        }
        scaffolds.remove(pos);
        setDirty();
        return true;
    }

    private List<Map.Entry<BlockPos, Long>> sortedWebs() {
        List<Map.Entry<BlockPos, Long>> values = new ArrayList<>(
                webs.entrySet());
        values.sort(Comparator.comparingLong(entry ->
                entry.getKey().asLong()));
        return values;
    }

    private List<Map.Entry<BlockPos, ScaffoldEntry>> sortedScaffolds() {
        List<Map.Entry<BlockPos, ScaffoldEntry>> values = new ArrayList<>(
                scaffolds.entrySet());
        values.sort(Comparator.comparingLong(entry ->
                entry.getKey().asLong()));
        return values;
    }

    public record ScaffoldEntry(BlockState state, long expiresAt,
                                UUID ownerId) {
    }
}

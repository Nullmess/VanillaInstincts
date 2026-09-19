package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.block.state.IBlockState;
/**
 * Cache court des collisions de surface consultées par les araignées.
 */
public final class SpiderSurfaceCache {
    private static final Map<WorldServer, Map<BlockPos, Entry>> LEVELS =
            new WeakHashMap<>();

    private SpiderSurfaceCache() {
    }

    public static boolean hasCollision(WorldServer level, BlockPos pos,
                                       long gameTime) {
        synchronized (LEVELS) {
            Map<BlockPos, Entry> entries = LEVELS.computeIfAbsent(level,
                    ignored -> new HashMap<>());
            boolean loaded = level.isBlockLoaded(pos);
            IBlockState state = loaded ? level.getBlockState(pos) : null;
            Entry cached = entries.get(pos);
            if (cached != null && cached.expiresAt >= gameTime
                    && cached.state.equals(state)) {
                return cached.collision;
            }
            boolean collision = loaded
                    && state != null
                    && fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(state, level, pos);
            if (entries.size()
                    >= SpiderRules.MAX_SPIDER_SURFACE_CACHE_ENTRIES
                    && !entries.containsKey(pos)) {
                entries.entrySet().removeIf(entry ->
                        entry.getValue().expiresAt < gameTime);
                if (entries.size()
                        >= SpiderRules.MAX_SPIDER_SURFACE_CACHE_ENTRIES) {
                    entries.clear();
                }
            }
            entries.put(immutableBlockPos(pos), new Entry(state, collision,
                    gameTime + SpiderRules.SPIDER_SURFACE_CACHE_TICKS));
            return collision;
        }
    }

    public static int entryCount(WorldServer level) {
        synchronized (LEVELS) {
            Map<BlockPos, Entry> entries = LEVELS.get(level);
            return entries == null ? 0 : entries.size();
        }
    }

    public static void clearLevel(WorldServer level) {
        synchronized (LEVELS) {
            LEVELS.remove(level);
        }
    }

    private static class Entry {
        private final IBlockState state;
        private final boolean collision;
        private final long expiresAt;

        public Entry(IBlockState state, boolean collision, long expiresAt) {
            this.state = state;
            this.collision = collision;
            this.expiresAt = expiresAt;
        }

        public IBlockState state() { return this.state; }

        public boolean collision() { return this.collision; }

        public long expiresAt() { return this.expiresAt; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Entry)) return false;
            Entry that = (Entry) other;
            return java.util.Objects.equals(this.state, that.state) && this.collision == that.collision && this.expiresAt == that.expiresAt;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.state, this.collision, this.expiresAt); }

        @Override
        public String toString() {
            return "Entry[" + "state=" + this.state + ", " + "collision=" + this.collision + ", " + "expiresAt=" + this.expiresAt + "]";
        }

    }
}

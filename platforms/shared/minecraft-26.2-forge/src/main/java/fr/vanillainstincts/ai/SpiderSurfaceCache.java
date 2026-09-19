package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
/**
 * Cache court des collisions de surface consultées par les araignées.
 */
public final class SpiderSurfaceCache {
    private static final Map<ServerLevel, Map<BlockPos, Entry>> LEVELS =
            new WeakHashMap<>();

    private SpiderSurfaceCache() {
    }

    public static boolean hasCollision(ServerLevel level, BlockPos pos,
                                       long gameTime) {
        synchronized (LEVELS) {
            Map<BlockPos, Entry> entries = LEVELS.computeIfAbsent(level,
                    ignored -> new HashMap<>());
            boolean loaded = level.isLoaded(pos);
            BlockState state = loaded ? level.getBlockState(pos) : null;
            Entry cached = entries.get(pos);
            if (cached != null && cached.expiresAt >= gameTime
                    && cached.state.equals(state)) {
                return cached.collision;
            }
            boolean collision = loaded
                    && state != null
                    && !state.getCollisionShape(level, pos).isEmpty();
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
            entries.put(pos.immutable(), new Entry(state, collision,
                    gameTime + SpiderRules.SPIDER_SURFACE_CACHE_TICKS));
            return collision;
        }
    }

    public static int entryCount(ServerLevel level) {
        synchronized (LEVELS) {
            Map<BlockPos, Entry> entries = LEVELS.get(level);
            return entries == null ? 0 : entries.size();
        }
    }

    public static void clearLevel(ServerLevel level) {
        synchronized (LEVELS) {
            LEVELS.remove(level);
        }
    }

    private record Entry(BlockState state, boolean collision, long expiresAt) {
    }
}

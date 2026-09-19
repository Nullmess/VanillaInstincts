package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import fr.vanillainstincts.data.FarmerCropRegistry;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.passive.EntityVillager;
/** File bornée des cultures détruites qu'un fermier a réellement observées. */
public final class FarmerReplantQueue {
    private static final Map<WorldServer, Map<UUID, LinkedHashMap<Long, Long>>>
            QUEUES = Collections.synchronizedMap(new WeakHashMap<>());

    private FarmerReplantQueue() {
    }

    public static void enqueue(WorldServer level, EntityVillager farmer,
                               BlockPos pos, long expiresAt) {
        if (level == null || farmer == null || pos == null) return;
        LinkedHashMap<Long, Long> queue = queue(level, farmer.getUniqueID());
        queue.put(pos.toLong(), expiresAt);
        while (queue.size() > VillageSocialRules.FARMER_REPLANT_QUEUE_LIMIT) {
            Iterator<Long> iterator = queue.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    public static BlockPos next(WorldServer level, EntityVillager farmer,
                                long gameTime) {
        if (level == null || farmer == null) return null;
        LinkedHashMap<Long, Long> queue = queue(level, farmer.getUniqueID());
        Iterator<Map.Entry<Long, Long>> iterator = queue.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            BlockPos pos = BlockPos.fromLong(entry.getKey());
            if (entry.getValue() < gameTime || !level.isBlockLoaded(pos)
                    || !FarmerCropRegistry.hasPotentialPlantingGround(
                    level, pos)) {
                iterator.remove();
                continue;
            }
            if (fr.vanillainstincts.compat.Minecraft112Compat.isAir(level.getBlockState(pos))) {
                return immutableBlockPos(pos);
            }
            // BreakEvent est envoyé juste avant la disparition effective du
            // bloc. On conserve donc brièvement une culture encore présente
            // au lieu de perdre la mémoire de la casse dès le même tick.
            if (FarmerController.isPlayerReplantCandidate(
                    level.getBlockState(pos))) {
                continue;
            }
            iterator.remove();
        }
        return null;
    }

    public static void complete(WorldServer level, EntityVillager farmer,
                                BlockPos pos) {
        if (level == null || farmer == null || pos == null) return;
        queue(level, farmer.getUniqueID()).remove(pos.toLong());
    }

    public static int size(WorldServer level, EntityVillager farmer, long gameTime) {
        next(level, farmer, gameTime);
        return queue(level, farmer.getUniqueID()).size();
    }

    public static void clearLevel(WorldServer level) {
        QUEUES.remove(level);
    }

    private static LinkedHashMap<Long, Long> queue(WorldServer level,
                                                    UUID farmerId) {
        synchronized (QUEUES) {
            return QUEUES.computeIfAbsent(level, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(farmerId, ignored -> new LinkedHashMap<>());
        }
    }
}

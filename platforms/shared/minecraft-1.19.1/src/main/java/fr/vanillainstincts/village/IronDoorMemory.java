package fr.vanillainstincts.village;

import fr.vanillainstincts.VanillaInstincts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Mémoire persistante des portes en fer dont un villageois a observé le
 * mécanisme. La mémoire est copiée lors d'une zombification ou d'une guérison.
 */
public final class IronDoorMemory {
    private static final String ROOT_KEY = VanillaInstincts.MOD_ID + "_iron_doors";
    private static final String ENTRIES_KEY = "entries";
    private static final int DATA_VERSION = 1;
    private static final int MAX_ENTRIES = 24;

    private IronDoorMemory() {
    }

    public static void remember(Entity entity, ServerLevel level,
                                BlockPos door, BlockPos mechanism,
                                long gameTime) {
        if (entity == null || level == null || door == null
                || mechanism == null) {
            return;
        }
        CompoundTag root = root(entity);
        ListTag current = root.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
        ListTag next = new ListTag();
        String dimension = level.dimension().location().toString();

        CompoundTag learned = new CompoundTag();
        learned.putLong("door", door.asLong());
        learned.putLong("mechanism", mechanism.asLong());
        learned.putString("dimension", dimension);
        learned.putLong("learned_at", Math.max(0L, gameTime));
        next.add(learned);

        for (int index = 0; index < current.size()
                && next.size() < MAX_ENTRIES; index++) {
            CompoundTag entry = current.getCompound(index);
            if (entry.getLong("door") == door.asLong()
                    && dimension.equals(entry.getString("dimension"))) {
                continue;
            }
            next.add(entry.copy());
        }
        root.putInt("data_version", DATA_VERSION);
        root.put(ENTRIES_KEY, next);
        entity.getPersistentData().put(ROOT_KEY, root);
    }

    public static List<LearnedDoor> entries(Entity entity,
                                             ServerLevel level) {
        List<LearnedDoor> result = new ArrayList<>();
        if (entity == null || level == null
                || !entity.getPersistentData().contains(ROOT_KEY)) {
            return result;
        }
        String dimension = level.dimension().location().toString();
        ListTag list = entity.getPersistentData().getCompound(ROOT_KEY)
                .getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (!dimension.equals(entry.getString("dimension"))) {
                continue;
            }
            result.add(new LearnedDoor(
                    BlockPos.of(entry.getLong("door")),
                    BlockPos.of(entry.getLong("mechanism")),
                    Math.max(0L, entry.getLong("learned_at"))));
        }
        return result;
    }

    public static boolean knows(Entity entity, ServerLevel level,
                                BlockPos door) {
        if (door == null) return false;
        return entries(entity, level).stream()
                .anyMatch(entry -> entry.door().equals(door));
    }

    public static void copy(Entity source, Entity outcome) {
        if (source == null || outcome == null
                || !source.getPersistentData().contains(ROOT_KEY)) {
            return;
        }
        outcome.getPersistentData().put(ROOT_KEY,
                source.getPersistentData().getCompound(ROOT_KEY).copy());
    }

    public static int size(Entity entity, ServerLevel level) {
        return entries(entity, level).size();
    }

    private static CompoundTag root(Entity entity) {
        if (entity.getPersistentData().contains(ROOT_KEY)) {
            return entity.getPersistentData().getCompound(ROOT_KEY).copy();
        }
        CompoundTag root = new CompoundTag();
        root.putInt("data_version", DATA_VERSION);
        root.put(ENTRIES_KEY, new ListTag());
        return root;
    }

    public record LearnedDoor(BlockPos door, BlockPos mechanism,
                              long learnedAt) {
    }
}

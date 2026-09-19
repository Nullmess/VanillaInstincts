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
        ListTag current = fr.vanillainstincts.persistence.NbtCompat.getList(root, ENTRIES_KEY);
        ListTag next = new ListTag();
        String dimension = level.dimension().identifier().toString();

        CompoundTag learned = new CompoundTag();
        learned.putLong("door", door.asLong());
        learned.putLong("mechanism", mechanism.asLong());
        learned.putString("dimension", dimension);
        learned.putLong("learned_at", Math.max(0L, gameTime));
        next.add(learned);

        for (int index = 0; index < current.size()
                && next.size() < MAX_ENTRIES; index++) {
            CompoundTag entry = fr.vanillainstincts.persistence.NbtCompat.getCompound(current, index);
            if (fr.vanillainstincts.persistence.NbtCompat.getLong(entry, "door") == door.asLong()
                    && dimension.equals(fr.vanillainstincts.persistence.NbtCompat.getString(entry, "dimension"))) {
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
        String dimension = level.dimension().identifier().toString();
        ListTag list = fr.vanillainstincts.persistence.NbtCompat.getList(
                fr.vanillainstincts.persistence.NbtCompat.getCompound(entity.getPersistentData(), ROOT_KEY), ENTRIES_KEY);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = fr.vanillainstincts.persistence.NbtCompat.getCompound(list, index);
            if (!dimension.equals(fr.vanillainstincts.persistence.NbtCompat.getString(entry, "dimension"))) {
                continue;
            }
            result.add(new LearnedDoor(
                    BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(entry, "door")),
                    BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(entry, "mechanism")),
                    Math.max(0L, fr.vanillainstincts.persistence.NbtCompat.getLong(entry, "learned_at"))));
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
                fr.vanillainstincts.persistence.NbtCompat.getCompound(source.getPersistentData(), ROOT_KEY).copy());
    }

    public static int size(Entity entity, ServerLevel level) {
        return entries(entity, level).size();
    }

    private static CompoundTag root(Entity entity) {
        if (entity.getPersistentData().contains(ROOT_KEY)) {
            return fr.vanillainstincts.persistence.NbtCompat.getCompound(entity.getPersistentData(), ROOT_KEY).copy();
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

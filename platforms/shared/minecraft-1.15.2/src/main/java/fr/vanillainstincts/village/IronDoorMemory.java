package fr.vanillainstincts.village;

import fr.vanillainstincts.VanillaInstincts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;

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

    public static void remember(Entity entity, ServerWorld level,
                                BlockPos door, BlockPos mechanism,
                                long gameTime) {
        if (entity == null || level == null || door == null
                || mechanism == null) {
            return;
        }
        CompoundNBT root = root(entity);
        ListNBT current = root.getList(ENTRIES_KEY, 10);
        ListNBT next = new ListNBT();
        String dimension = String.valueOf(level.dimension.getType());

        CompoundNBT learned = new CompoundNBT();
        learned.putLong("door", door.asLong());
        learned.putLong("mechanism", mechanism.asLong());
        learned.putString("dimension", dimension);
        learned.putLong("learned_at", Math.max(0L, gameTime));
        next.add(learned);

        for (int index = 0; index < current.size()
                && next.size() < MAX_ENTRIES; index++) {
            CompoundNBT entry = current.getCompound(index);
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
                                             ServerWorld level) {
        List<LearnedDoor> result = new ArrayList<>();
        if (entity == null || level == null
                || !entity.getPersistentData().contains(ROOT_KEY)) {
            return result;
        }
        String dimension = String.valueOf(level.dimension.getType());
        ListNBT list = entity.getPersistentData().getCompound(ROOT_KEY)
                .getList(ENTRIES_KEY, 10);
        for (int index = 0; index < list.size(); index++) {
            CompoundNBT entry = list.getCompound(index);
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

    public static boolean knows(Entity entity, ServerWorld level,
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

    public static int size(Entity entity, ServerWorld level) {
        return entries(entity, level).size();
    }

    private static CompoundNBT root(Entity entity) {
        if (entity.getPersistentData().contains(ROOT_KEY)) {
            return entity.getPersistentData().getCompound(ROOT_KEY).copy();
        }
        CompoundNBT root = new CompoundNBT();
        root.putInt("data_version", DATA_VERSION);
        root.put(ENTRIES_KEY, new ListNBT());
        return root;
    }

    public static class LearnedDoor {
        private final BlockPos door;
        private final BlockPos mechanism;
        private final long learnedAt;

        public LearnedDoor(BlockPos door, BlockPos mechanism, long learnedAt) {
            this.door = door;
            this.mechanism = mechanism;
            this.learnedAt = learnedAt;
        }

        public BlockPos door() { return this.door; }

        public BlockPos mechanism() { return this.mechanism; }

        public long learnedAt() { return this.learnedAt; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof LearnedDoor)) return false;
            LearnedDoor that = (LearnedDoor) other;
            return java.util.Objects.equals(this.door, that.door) && java.util.Objects.equals(this.mechanism, that.mechanism) && this.learnedAt == that.learnedAt;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.door, this.mechanism, this.learnedAt); }

        @Override
        public String toString() {
            return "LearnedDoor[" + "door=" + this.door + ", " + "mechanism=" + this.mechanism + ", " + "learnedAt=" + this.learnedAt + "]";
        }

    }
}

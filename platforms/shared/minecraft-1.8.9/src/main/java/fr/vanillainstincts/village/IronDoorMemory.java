package fr.vanillainstincts.village;

import fr.vanillainstincts.VanillaInstincts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTBase;
import net.minecraft.world.WorldServer;
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

    public static void remember(Entity entity, WorldServer level,
                                BlockPos door, BlockPos mechanism,
                                long gameTime) {
        if (entity == null || level == null || door == null
                || mechanism == null) {
            return;
        }
        NBTTagCompound root = root(entity);
        NBTTagList current = root.getTagList(ENTRIES_KEY, 10);
        NBTTagList next = new NBTTagList();
        String dimension = String.valueOf(level.provider.getDimensionId());

        NBTTagCompound learned = new NBTTagCompound();
        learned.setLong("door", door.toLong());
        learned.setLong("mechanism", mechanism.toLong());
        learned.setString("dimension", dimension);
        learned.setLong("learned_at", Math.max(0L, gameTime));
        next.appendTag(learned);

        for (int index = 0; index < current.tagCount()
                && next.tagCount() < MAX_ENTRIES; index++) {
            NBTTagCompound entry = current.getCompoundTagAt(index);
            if (entry.getLong("door") == door.toLong()
                    && dimension.equals(entry.getString("dimension"))) {
                continue;
            }
            next.appendTag(entry.copy());
        }
        root.setInteger("data_version", DATA_VERSION);
        root.setTag(ENTRIES_KEY, next);
        entity.getEntityData().setTag(ROOT_KEY, root);
    }

    public static List<LearnedDoor> entries(Entity entity,
                                             WorldServer level) {
        List<LearnedDoor> result = new ArrayList<>();
        if (entity == null || level == null
                || !entity.getEntityData().hasKey(ROOT_KEY)) {
            return result;
        }
        String dimension = String.valueOf(level.provider.getDimensionId());
        NBTTagList list = entity.getEntityData().getCompoundTag(ROOT_KEY)
                .getTagList(ENTRIES_KEY, 10);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            if (!dimension.equals(entry.getString("dimension"))) {
                continue;
            }
            result.add(new LearnedDoor(
                    BlockPos.fromLong(entry.getLong("door")),
                    BlockPos.fromLong(entry.getLong("mechanism")),
                    Math.max(0L, entry.getLong("learned_at"))));
        }
        return result;
    }

    public static boolean knows(Entity entity, WorldServer level,
                                BlockPos door) {
        if (door == null) return false;
        return entries(entity, level).stream()
                .anyMatch(entry -> entry.door().equals(door));
    }

    public static void copy(Entity source, Entity outcome) {
        if (source == null || outcome == null
                || !source.getEntityData().hasKey(ROOT_KEY)) {
            return;
        }
        outcome.getEntityData().setTag(ROOT_KEY,
                source.getEntityData().getCompoundTag(ROOT_KEY).copy());
    }

    public static int size(Entity entity, WorldServer level) {
        return entries(entity, level).size();
    }

    private static NBTTagCompound root(Entity entity) {
        if (entity.getEntityData().hasKey(ROOT_KEY)) {
            return (NBTTagCompound) entity.getEntityData().getCompoundTag(ROOT_KEY).copy();
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("data_version", DATA_VERSION);
        root.setTag(ENTRIES_KEY, new NBTTagList());
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

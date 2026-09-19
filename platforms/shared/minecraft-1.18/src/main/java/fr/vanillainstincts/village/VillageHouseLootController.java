package fr.vanillainstincts.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

/** Restores the loot-table metadata carried by vanilla village templates. */
public final class VillageHouseLootController {
    private VillageHouseLootController() {
    }

    /**
     * Assigns the vanilla loot table lazily, exactly like a generated village
     * chest. Existing contents and an already assigned table are never reset.
     */
    public static boolean ensure(ServerLevel level, BlockPos position,
                                 ResourceLocation lootTable, long seed) {
        if (level == null || position == null || lootTable == null
                || !level.hasChunkAt(position)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) {
            return false;
        }
        ResourceLocation expected = lootTable;

        // Minecraft 1.18 has no public getLootTable()/setLootTableSeed()
        // accessors yet. Read the persisted metadata first so an existing
        // loot table is never replaced or unpacked just to inspect it.
        CompoundTag metadata = container.saveWithFullMetadata();
        if (metadata.contains("LootTable", Tag.TAG_STRING)) {
            // Any already assigned table is intentionally preserved.
            return true;
        }
        if (!container.isEmpty()) {
            return true;
        }
        long effectiveSeed = seed == 0L ? level.random.nextLong() : seed;
        container.setLootTable(expected, effectiveSeed);
        container.setChanged();
        return true;
    }
}

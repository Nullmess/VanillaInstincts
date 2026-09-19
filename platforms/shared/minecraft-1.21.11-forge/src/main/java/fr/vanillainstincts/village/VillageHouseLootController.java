package fr.vanillainstincts.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

/** Restores the loot-table metadata carried by vanilla village templates. */
public final class VillageHouseLootController {
    private VillageHouseLootController() {
    }

    /**
     * Assigns the vanilla loot table lazily, exactly like a generated village
     * chest. Existing contents and an already assigned table are never reset.
     */
    public static boolean ensure(ServerLevel level, BlockPos position,
                                 Identifier lootTable, long seed) {
        if (level == null || position == null || lootTable == null
                || !level.hasChunkAt(position)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) {
            return false;
        }
        ResourceKey<LootTable> expected = ResourceKey.create(
                Registries.LOOT_TABLE, lootTable);
        ResourceKey<LootTable> current = container.getLootTable();
        if (expected.equals(current)) return true;
        if (current != null || !container.isEmpty()) {
            return true;
        }
        container.setLootTable(expected);
        container.setLootTableSeed(seed == 0L ? level.random.nextLong() : seed);
        container.setChanged();
        return expected.equals(container.getLootTable());
    }
}

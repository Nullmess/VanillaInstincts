package fr.vanillainstincts.village;

import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.LockableLootTileEntity;

/** Restores the loot-table metadata carried by vanilla village templates. */
public final class VillageHouseLootController {
    private VillageHouseLootController() {
    }

    /**
     * Assigns the vanilla loot table lazily, exactly like a generated village
     * chest. Existing contents and an already assigned table are never reset.
     */
    public static boolean ensure(ServerWorld level, BlockPos position,
                                 ResourceLocation lootTable, long seed) {
        if (level == null || position == null || lootTable == null
                || !level.hasChunkAt(position)) {
            return false;
        }
        TileEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof LockableLootTileEntity)) {
            return false;
        } LockableLootTileEntity container = (LockableLootTileEntity) (blockEntity);
        ResourceLocation expected = lootTable;

        // Minecraft 1.18 has no public getLootTable()/setLootTableSeed()
        // accessors yet. Read the persisted metadata first so an existing
        // loot table is never replaced or unpacked just to inspect it.
        CompoundNBT metadata = container.save(new CompoundNBT());
        if (metadata.contains("LootTable", 8)) {
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

package fr.vanillainstincts.village;

import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;

/** Restores the loot-table metadata carried by vanilla village templates. */
public final class VillageHouseLootController {
    private VillageHouseLootController() {
    }

    /**
     * Assigns the vanilla loot table lazily, exactly like a generated village
     * chest. Existing contents and an already assigned table are never reset.
     */
    public static boolean ensure(WorldServer level, BlockPos position,
                                 ResourceLocation lootTable, long seed) {
        if (level == null || position == null || lootTable == null
                || !level.isBlockLoaded(position)) {
            return false;
        }
        TileEntity blockEntity = level.getTileEntity(position);
        if (!(blockEntity instanceof TileEntityLockableLoot)) {
            return false;
        } TileEntityLockableLoot container = (TileEntityLockableLoot) (blockEntity);
        ResourceLocation expected = lootTable;

        // Minecraft 1.18 has no public getLootTable()/setLootTableSeed()
        // accessors yet. Read the persisted metadata first so an existing
        // loot table is never replaced or unpacked just to inspect it.
        NBTTagCompound metadata = container.writeToNBT(new NBTTagCompound());
        if (metadata.hasKey("LootTable", 8)) {
            // Any already assigned table is intentionally preserved.
            return true;
        }
        if (hasContents(container)) {
            return true;
        }
        long effectiveSeed = seed == 0L ? level.rand.nextLong() : seed;
        container.setLootTable(expected, effectiveSeed);
        container.markDirty();
        return true;
    }
    private static boolean hasContents(TileEntityLockableLoot container) {
        for (int slot = 0; slot < container.getSizeInventory(); slot++) {
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(
                    container.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }
}

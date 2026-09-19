package fr.vanillainstincts.village;

import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

/**
 * LootTable-backed lockable containers were introduced after 1.8.x.
 * Vanilla 1.8 village chests are populated during structure generation, so
 * there is no later loot-table metadata to restore here.
 */
public final class VillageHouseLootController {
    private VillageHouseLootController() {}

    public static boolean ensure(WorldServer level, BlockPos position,
                                 ResourceLocation lootTable, long seed) {
        return false;
    }
}

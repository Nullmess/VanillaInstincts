package fr.vanillainstincts.village;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/** One block mutation planned for a village project. */
record VillagePlacement(BlockPos pos, BlockState state,
                        boolean terrainReplacement, boolean road,
                        Identifier lootTable, long lootTableSeed) {
    VillagePlacement(BlockPos pos, BlockState state,
                     boolean terrainReplacement, boolean road) {
        this(pos, state, terrainReplacement, road, null, 0L);
    }

    boolean hasLootTable() {
        return lootTable != null;
    }
}

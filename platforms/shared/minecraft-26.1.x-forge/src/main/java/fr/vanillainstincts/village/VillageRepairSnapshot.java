package fr.vanillainstincts.village;

import java.util.Map;
import net.minecraft.core.BlockPos;

/** Immutable nighttime snapshot of one loaded village area. */
record VillageRepairSnapshot(BlockPos center,
                             Map<BlockPos, VillageSnapshotBlock> blocks) {
}

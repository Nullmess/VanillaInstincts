package fr.vanillainstincts.village;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.block.state.IBlockState;

/** Mutable runtime cursor for one village repair operation. */
final class VillageRepairTask {
    final BlockPos pos;
    final IBlockState expected;
    final LegacyVillagerProfession profession;
    final boolean terrainReplacement;
    final boolean road;
    UUID builderId;
    BlockPos accessPos;
    int stallSamples;
    int attempts;
    final Set<Long> rejectedAccess = new HashSet<>();
    double lastDistanceSqr = Double.POSITIVE_INFINITY;

    VillageRepairTask(BlockPos pos, IBlockState expected,
                      LegacyVillagerProfession profession,
                      boolean terrainReplacement, boolean road,
                      UUID builderId) {
        this.pos = pos;
        this.expected = expected;
        this.profession = profession;
        this.terrainReplacement = terrainReplacement;
        this.road = road;
        this.builderId = builderId;
    }
}

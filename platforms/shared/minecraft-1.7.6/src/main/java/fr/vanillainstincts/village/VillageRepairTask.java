package fr.vanillainstincts.village;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import fr.vanillainstincts.compat.BlockPos;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import fr.vanillainstincts.compat.LegacyBlockState;

/** Mutable runtime cursor for one village repair operation. */
final class VillageRepairTask {
    final BlockPos pos;
    final LegacyBlockState expected;
    final LegacyVillagerProfession profession;
    final boolean terrainReplacement;
    final boolean road;
    UUID builderId;
    BlockPos accessPos;
    int stallSamples;
    int attempts;
    final Set<Long> rejectedAccess = new HashSet<>();
    double lastDistanceSqr = Double.POSITIVE_INFINITY;

    VillageRepairTask(BlockPos pos, LegacyBlockState expected,
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

package fr.vanillainstincts.village;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.block.BlockState;

/** Mutable runtime cursor for one village repair operation. */
final class VillageRepairTask {
    final BlockPos pos;
    final BlockState expected;
    final VillagerProfession profession;
    final boolean terrainReplacement;
    final boolean road;
    UUID builderId;
    BlockPos accessPos;
    int stallSamples;
    int attempts;
    final Set<Long> rejectedAccess = new HashSet<>();
    double lastDistanceSqr = Double.POSITIVE_INFINITY;

    VillageRepairTask(BlockPos pos, BlockState expected,
                      VillagerProfession profession,
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

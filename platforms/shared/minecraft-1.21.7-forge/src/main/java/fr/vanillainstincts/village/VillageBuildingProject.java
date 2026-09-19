package fr.vanillainstincts.village;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Rotation;

/** Mutable runtime cursor for one village construction project. */
final class VillageBuildingProject {
    final BlockPos center;
    final BlockPos origin;
    final VillageEvolutionController.BuildingKind kind;
    final VillagerProfession profession;
    final ResourceLocation templateId;
    final Rotation rotation;
    final List<VillagePlacement> placements;
    UUID builderId;
    int index;
    final Map<Integer, Integer> failures = new HashMap<>();
    final Set<Integer> ignoredPlacements = new HashSet<>();
    final Set<Long> rejectedAccess = new HashSet<>();
    BlockPos accessPos;
    int accessIndex = -1;
    int stallSamples;
    double lastDistanceSqr = Double.POSITIVE_INFINITY;

    VillageBuildingProject(BlockPos center, BlockPos origin,
                           VillageEvolutionController.BuildingKind kind,
                           VillagerProfession profession,
                           ResourceLocation templateId,
                           Rotation rotation,
                           List<VillagePlacement> placements,
                           UUID builderId) {
        this.center = center;
        this.origin = origin;
        this.kind = kind;
        this.profession = profession;
        this.templateId = templateId;
        this.rotation = rotation;
        this.placements = placements;
        this.builderId = builderId;
    }
}

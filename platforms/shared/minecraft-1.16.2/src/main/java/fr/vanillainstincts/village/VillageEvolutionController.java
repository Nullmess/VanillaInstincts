package fr.vanillainstincts.village;

import net.minecraft.util.registry.Registry;
import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.core.model.IntBounds3D;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.ai.VanillaInstinctsWorkLimiter;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.core.policy.VillageGrowthPolicy;
import fr.vanillainstincts.core.policy.VillageTemplateSelectionPolicy;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.memory.MemoryModuleType;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.item.ItemStack;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.Rotation;
import net.minecraft.block.BlockState;
/** Bâtiments vanilla. */
public final class VillageEvolutionController {
    private static final Map<ServerWorld, VillageEvolutionRuntime> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Rotation[] ROTATIONS = Rotation.values();
    private VillageEvolutionController() {
    }

    public static void tickLevel(ServerWorld level, long gameTime) {
        if (level == null) return;
        VillageEvolutionRuntime state = state(level);
        long dayTime = Math.floorMod(level.getDayTime(), 24_000L);
        long day = Math.floorDiv(level.getDayTime(), 24_000L);

        if (FeatureGate.enabled(FeatureFlag.VILLAGE_REPAIRS, level)
                && dayTime >= 12_000L && state.snapshotDay != day
                && allow(level, VanillaInstinctsWorkLimiter.Task.NIGHT_SNAPSHOT,
                gameTime, 1L, 1_000L)) {
            captureNightSnapshots(level, state, day);
        }
        if (FeatureGate.enabled(FeatureFlag.VILLAGE_REPAIRS, level)
                && dayTime < 12_000L && state.repairPreparedDay != day
                && allow(level, VanillaInstinctsWorkLimiter.Task.REPAIR_PREPARATION,
                gameTime, 1L, 1_000L)) {
            prepareRepairs(level, state, day);
        }

        restoreProject(level, state);
        if (VillagerRoutineController.phaseFor(level.getDayTime())
                == VillagerSchedulePhase.WORK) {
            if (FeatureGate.enabled(FeatureFlag.VILLAGE_REPAIRS, level)) {
                processRepair(level, state, gameTime);
            }
            processBuilding(level, state, gameTime);
        }
        if (allow(level, VanillaInstinctsWorkLimiter.Task.STRUCTURE_MEMORY,
                gameTime,
                RuntimeConfig.interval(FeatureFlag.VILLAGE_CONSTRUCTION,
                VillageConstructionRules.VILLAGE_STRUCTURE_MEMORY_SCAN_TICKS),
                RuntimeConfig.interval(FeatureFlag.VILLAGE_CONSTRUCTION,
                VillageConstructionRules.VILLAGE_STRUCTURE_MEMORY_REALTIME_MILLIS))) {
            refreshKnownStructures(level);
        }
        if (state.project == null && state.repairs.isEmpty()
                && allow(level, VanillaInstinctsWorkLimiter.Task.VILLAGE_DISCOVERY,
                gameTime, RuntimeConfig.interval(FeatureFlag.VILLAGE_CONSTRUCTION,
                VillageConstructionRules.VILLAGE_EVOLUTION_SCAN_TICKS),
                RuntimeConfig.interval(FeatureFlag.VILLAGE_CONSTRUCTION,
                VillageConstructionRules.VILLAGE_EVOLUTION_REALTIME_MILLIS))) {
            state.project = discoverProject(level);
        }
    }

    private static boolean allow(ServerWorld level,
                                 VanillaInstinctsWorkLimiter.Task task,
                                 long gameTime, long ticks, long millis) {
        return VanillaInstinctsWorkLimiter.allow(level, task, gameTime, ticks, millis);
    }

    public static void clearLevel(ServerWorld level) {
        STATES.remove(level);
        VanillaVillageStructureCatalog.clear(level);
    }

    private static void processBuilding(ServerWorld level, VillageEvolutionRuntime state,
                                        long gameTime) {
        VillageBuildingProject project = state.project;
        if (project == null || !allow(level,
                VanillaInstinctsWorkLimiter.Task.BUILD_PLACE, gameTime,
                RuntimeConfig.interval(FeatureFlag.VILLAGE_CONSTRUCTION,
                VillageConstructionRules.VILLAGE_BUILD_PLACE_TICKS),
                RuntimeConfig.interval(FeatureFlag.VILLAGE_CONSTRUCTION,
                VillageConstructionRules.VILLAGE_BUILD_REALTIME_MILLIS))) {
            return;
        }

        advanceAlreadyFinished(level, project, 128);
        if (project.index >= project.placements.size()) {
            int missing = firstUnfinished(level, project);
            if (missing >= 0) {
                project.index = missing;
                resetAccess(project);
                VillageEvolutionSavedData.get(level).progress(project.index,
                        project.builderId);
                return;
            }
            clearBuilderHand(level, project.builderId);
            rememberProject(level, project);
            state.project = null;
            VillageEvolutionSavedData.get(level).clear();
            return;
        }

        int placementIndex = project.index;
        VillagePlacement placement = project.placements.get(placementIndex);
        if (placement.road()
                && !FeatureGate.enabled(FeatureFlag.VILLAGE_ROADS, level)) {
            ignorePlacement(level, project, placementIndex);
            return;
        }
        if (!level.hasChunkAt(placement.pos())) return;
        boolean simpleHouse = project.kind == BuildingKind.HOUSE;
        VillagerProfession owner = placement.road()
                ? VillagerProfession.NONE : project.profession;
        VillagerEntity builder = builder(level, project.builderId, placement.pos(),
                owner, placement.road(), simpleHouse);
        if (builder == null) {
            // Route
            if (placement.road()) {
                ignorePlacement(level, project, placementIndex);
            }
            return;
        }
        project.builderId = builder.getUUID();

        int failures = project.failures.getOrDefault(placementIndex, 0);
        if (project.accessIndex != placementIndex || project.accessPos == null) {
            project.accessPos = VillageConstructionAccess.find(level, builder,
                    placement.pos(), project.rejectedAccess);
            project.accessIndex = placementIndex;
            project.stallSamples = 0;
            project.lastDistanceSqr = Double.POSITIVE_INFINITY;
        }

        if (project.accessPos == null) {
            int attempts = incrementFailure(project, placementIndex);
            if (attempts >= VillageConstructionRules.VILLAGE_BUILDER_FORCE_ATTEMPTS) {
                if (placeVisible(level, builder, placement, true)) {
                    completePlacement(level, project, placementIndex);
                } else if (protectedConflict(level, placement)
                        || attempts >= VillageConstructionRules.VILLAGE_BUILDER_FORCE_ATTEMPTS
                        + 2) {
                    ignorePlacement(level, project, placementIndex);
                }
            }
            return;
        }

        double distance = builder.blockPosition().distSqr(project.accessPos);
        if (distance > 2.25D) {
            boolean moving = builder.getNavigation().moveTo(
                    project.accessPos.getX() + 0.5D,
                    project.accessPos.getY(),
                    project.accessPos.getZ() + 0.5D,
                    VillageConstructionRules.VILLAGE_BUILDER_SPEED);
            if (!moving || distance >= project.lastDistanceSqr - 0.20D) {
                project.stallSamples++;
            } else {
                project.stallSamples = 0;
            }
            project.lastDistanceSqr = distance;
            if (project.stallSamples
                    >= VillageConstructionRules.VILLAGE_BUILDER_STALL_SAMPLES) {
                project.rejectedAccess.add(project.accessPos.asLong());
                incrementFailure(project, placementIndex);
                project.accessPos = null;
                project.stallSamples = 0;
                project.lastDistanceSqr = Double.POSITIVE_INFINITY;
            }
            return;
        }

        boolean force = failures
                >= VillageConstructionRules.VILLAGE_BUILDER_FORCE_ATTEMPTS;
        if (VillageConstructionAccess.canWorkFrom(project.accessPos,
                placement.pos())
                && placeVisible(level, builder, placement, force)) {
            completePlacement(level, project, placementIndex);
        } else if (force && protectedConflict(level, placement)) {
            ignorePlacement(level, project, placementIndex);
        } else {
            project.rejectedAccess.add(project.accessPos.asLong());
            incrementFailure(project, placementIndex);
            project.accessPos = null;
            project.stallSamples = 0;
            project.lastDistanceSqr = Double.POSITIVE_INFINITY;
        }
    }

    private static void completePlacement(ServerWorld level,
                                          VillageBuildingProject project,
                                          int placementIndex) {
        project.failures.remove(placementIndex);
        project.index = placementIndex + 1;
        resetAccess(project);
        VillageEvolutionSavedData.get(level).progress(project.index,
                project.builderId);
    }

    private static int incrementFailure(VillageBuildingProject project,
                                        int placementIndex) {
        return project.failures.merge(placementIndex, 1, Integer::sum);
    }

    private static void ignorePlacement(ServerWorld level,
                                        VillageBuildingProject project,
                                        int placementIndex) {
        project.ignoredPlacements.add(placementIndex);
        project.failures.remove(placementIndex);
        project.index = placementIndex + 1;
        resetAccess(project);
        VillageEvolutionSavedData.get(level).progress(project.index,
                project.builderId);
    }

    private static void resetAccess(VillageBuildingProject project) {
        project.accessPos = null;
        project.accessIndex = -1;
        project.stallSamples = 0;
        project.lastDistanceSqr = Double.POSITIVE_INFINITY;
        project.rejectedAccess.clear();
    }

    private static void advanceAlreadyFinished(ServerWorld level,
                                                VillageBuildingProject project,
                                                int limit) {
        int advanced = 0;
        while (project.index < project.placements.size() && advanced++ < limit
                && placementSatisfied(level, project, project.index)) {
            project.index++;
        }
    }

    private static int firstUnfinished(ServerWorld level,
                                       VillageBuildingProject project) {
        for (int index = 0; index < project.placements.size(); index++) {
            if (!placementSatisfied(level, project, index)) return index;
        }
        return -1;
    }

    private static boolean placementSatisfied(ServerWorld level,
                                               VillageBuildingProject project,
                                               int index) {
        if (project.ignoredPlacements.contains(index)) return true;
        VillagePlacement placement = project.placements.get(index);
        if (!level.hasChunkAt(placement.pos())) return false;
        BlockState current = level.getBlockState(placement.pos());
        if (current.equals(placement.state())) {
            return !placement.hasLootTable()
                    || VillageHouseLootController.ensure(level, placement.pos(),
                    placement.lootTable(), placement.lootTableSeed());
        }
        return protectedConflict(level, placement);
    }

    private static boolean protectedConflict(ServerWorld level,
                                             VillagePlacement placement) {
        BlockState current = level.getBlockState(placement.pos());
        return VillageConstructionSafety.conflicts(level, placement.pos(),
                current, placement.state(), placement.terrainReplacement(),
                placement.road());
    }

    private static void processRepair(ServerWorld level, VillageEvolutionRuntime state,
                                      long gameTime) {
        if (state.repairs.isEmpty() || !allow(level,
                VanillaInstinctsWorkLimiter.Task.REPAIR_PLACE, gameTime,
                RuntimeConfig.interval(FeatureFlag.VILLAGE_CONSTRUCTION,
                VillageConstructionRules.VILLAGE_REPAIR_PLACE_TICKS),
                RuntimeConfig.interval(FeatureFlag.VILLAGE_CONSTRUCTION,
                VillageConstructionRules.VILLAGE_REPAIR_REALTIME_MILLIS))) {
            return;
        }
        VillageRepairTask task = state.repairs.peek();
        if (task == null) return;
        if (!level.hasChunkAt(task.pos)) {
            rotateRepair(state, task, true);
            return;
        }
        if (level.getBlockState(task.pos).equals(task.expected)) {
            state.repairs.remove();
            return;
        }
        VillagePlacement repairPlacement = new VillagePlacement(task.pos, task.expected,
                task.terrainReplacement, task.road);
        if (protectedConflict(level, repairPlacement)) {
            state.repairs.remove();
            return;
        }
        VillagerEntity builder = builder(level, task.builderId, task.pos,
                task.profession, task.road, !task.road
                && task.profession == VillagerProfession.NONE);
        if (builder == null) {
            rotateRepair(state, task, true);
            return;
        }
        task.builderId = builder.getUUID();
        if (task.accessPos == null) {
            task.accessPos = VillageConstructionAccess.find(level, builder,
                    task.pos, task.rejectedAccess);
            task.stallSamples = 0;
            task.lastDistanceSqr = Double.POSITIVE_INFINITY;
        }
        if (task.accessPos == null) {
            task.attempts++;
            if (task.attempts >= VillageConstructionRules.VILLAGE_BUILDER_FORCE_ATTEMPTS
                    && placeVisible(level, builder, repairPlacement, true)) {
                state.repairs.remove();
            } else if (task.attempts
                    >= VillageConstructionRules.VILLAGE_BUILDER_FORCE_ATTEMPTS + 2) {
                state.repairs.remove();
            } else {
                rotateRepair(state, task, true);
            }
            return;
        }

        double distance = builder.blockPosition().distSqr(task.accessPos);
        if (distance > 2.25D) {
            boolean moving = builder.getNavigation().moveTo(
                    task.accessPos.getX() + 0.5D, task.accessPos.getY(),
                    task.accessPos.getZ() + 0.5D,
                    VillageConstructionRules.VILLAGE_BUILDER_SPEED);
            if (!moving || distance >= task.lastDistanceSqr - 0.20D) {
                task.stallSamples++;
            } else {
                task.stallSamples = 0;
            }
            task.lastDistanceSqr = distance;
            if (task.stallSamples
                    >= VillageConstructionRules.VILLAGE_BUILDER_STALL_SAMPLES) {
                task.attempts++;
                task.rejectedAccess.add(task.accessPos.asLong());
                rotateRepair(state, task, false);
            }
            return;
        }

        boolean force = task.attempts
                >= VillageConstructionRules.VILLAGE_BUILDER_FORCE_ATTEMPTS;
        if (placeVisible(level, builder, repairPlacement, force)) {
            state.repairs.remove();
        } else {
            task.attempts++;
            if (task.attempts
                    >= VillageConstructionRules.VILLAGE_BUILDER_FORCE_ATTEMPTS + 2) {
                state.repairs.remove();
            } else {
                task.rejectedAccess.add(task.accessPos.asLong());
                rotateRepair(state, task, false);
            }
        }
    }

    private static void rotateRepair(VillageEvolutionRuntime state, VillageRepairTask task,
                                     boolean clearRejected) {
        if (state.repairs.peek() != task) return;
        state.repairs.remove();
        task.accessPos = null;
        task.stallSamples = 0;
        task.lastDistanceSqr = Double.POSITIVE_INFINITY;
        if (clearRejected) task.rejectedAccess.clear();
        state.repairs.add(task);
    }

    private static boolean placeVisible(ServerWorld level, VillagerEntity builder,
                                        VillagePlacement placement) {
        return placeVisible(level, builder, placement, false);
    }

    private static boolean placeVisible(ServerWorld level, VillagerEntity builder,
                                        VillagePlacement placement,
                                        boolean remotePlacement) {
        BlockState desired = placement.state();
        BlockState current = level.getBlockState(placement.pos());
        if (current.equals(desired)) {
            return !placement.hasLootTable()
                    || VillageHouseLootController.ensure(level, placement.pos(),
                    placement.lootTable(), placement.lootTableSeed());
        }

        // Secours
        if (!VillageConstructionSafety.canApply(level, placement.pos(), current,
                desired, placement.terrainReplacement(), placement.road())) {
            return false;
        }
        if (!desired.isAir()) {
            ItemStack display = new ItemStack(desired.getBlock().asItem());
            if (!display.isEmpty()) {
                builder.setItemInHand(Hand.MAIN_HAND, display);
            }
        }
        builder.swing(Hand.MAIN_HAND);
        WorldActionType action = current.isAir()
                ? WorldActionType.PLACE_BLOCK
                : WorldActionType.REPLACE_BLOCK;
        if (!WorldPermissionService.setBlock(level, builder, placement.pos(),
                desired, 3, action)) {
            return false;
        }
        builder.playWorkSound();
        if (!level.getBlockState(placement.pos()).equals(desired)) return false;
        return !placement.hasLootTable()
                || VillageHouseLootController.ensure(level, placement.pos(),
                placement.lootTable(), placement.lootTableSeed());
    }

    private static void restoreProject(ServerWorld level, VillageEvolutionRuntime state) {
        if (state.project != null || state.restoreAttempted) return;
        state.restoreAttempted = true;
        VillageEvolutionSavedData.Project saved =
                VillageEvolutionSavedData.get(level).project();
        if (saved == null) return;

        BuildingKind kind;
        try {
            kind = BuildingKind.valueOf(saved.kind());
        } catch (IllegalArgumentException exception) {
            VillageEvolutionSavedData.get(level).clear();
            return;
        }
        VillagerProfession profession = profession(saved.profession());
        BuildingRequest request = new BuildingRequest(kind, profession);
        VillageBiomeStyle style = VillageBiomeStyle.at(level, saved.center());

        ResourceLocation templateId = parseId(saved.templateId());
        VanillaVillageStructureCatalog.TemplatePlan template = templateId == null
                ? selectTemplate(level, saved.center(), request, style)
                : VanillaVillageStructureCatalog.plan(level, templateId);
        if (template == null) {
            VillageEvolutionSavedData.get(level).clear();
            return;
        }
        Rotation rotation = parseRotation(saved.rotation());
        String ownKey = template.id() + "|" + rotation + "|"
                + saved.origin().asLong();
        if (overlapsDifferentKnownStructure(level, saved.origin(), template,
                rotation, ownKey)) {
            VillageEvolutionSavedData.get(level).clear();
            return;
        }
        List<VillagePlacement> placements = vanillaBlueprint(level, saved.center(),
                saved.origin(), template, rotation, style, true);
        if (placements.isEmpty()) {
            VillageEvolutionSavedData.get(level).clear();
            return;
        }

        VillageBuildingProject restored = new VillageBuildingProject(saved.center(),
                saved.origin(), kind, profession, template.id(), rotation,
                placements, saved.builderId());
        restored.index = Math.min(saved.index(), placements.size());
        state.project = restored;

        // Imports structures recorded before template identity existed.
        if (saved.templateId() == null || saved.templateId().trim().isEmpty()) {
            VillageEvolutionSavedData.get(level).begin(saved.center(),
                    saved.origin(), kind.name(), professionName(profession),
                    template.id().toString(), rotation.name(), restored.index,
                    saved.builderId());
        }
    }

    private static void rememberProject(ServerWorld level,
                                        VillageBuildingProject project) {
        if (project == null) return;
        VillageEvolutionSavedData.get(level).rememberStructure(project.center,
                project.origin, project.kind.name(),
                professionName(project.profession),
                project.templateId.toString(), project.rotation.name());
    }

    private static VillageBuildingProject discoverRememberedProject(
            ServerWorld level, List<VillagerEntity> villagers) {
        VillageEvolutionSavedData data = VillageEvolutionSavedData.get(level);
        for (VillageEvolutionSavedData.KnownStructure remembered
                : data.knownStructures()) {
            ResourceLocation id = parseId(remembered.templateId());
            VanillaVillageStructureCatalog.TemplatePlan template =
                    VanillaVillageStructureCatalog.plan(level, id);
            if (template == null) {
                data.forgetStructure(remembered);
                continue;
            }
            Rotation rotation = parseRotation(remembered.rotation());
            BuildingKind kind;
            try {
                kind = BuildingKind.valueOf(remembered.kind());
            } catch (IllegalArgumentException exception) {
                kind = BuildingKind.HOUSE;
            }
            VillagerProfession profession = profession(remembered.profession());
            if (overlapsDifferentKnownStructure(level, remembered.origin(),
                    template, rotation, remembered.key())) {
                continue;
            }
            VillageBiomeStyle style = VillageBiomeStyle.at(level,
                    remembered.center());
            List<VillagePlacement> placements = vanillaBlueprint(level,
                    remembered.center(), remembered.origin(), template,
                    rotation, style, false);
            int first = firstRepairableMismatch(level, placements);
            if (first < 0) continue;
            List<VillagerEntity> community = villagers.stream()
                    .filter(villager -> villager.blockPosition().distSqr(
                            remembered.origin()) <=
                            RuntimeConfig.snapshot().villageBuildRadius()
                                    * RuntimeConfig.snapshot().villageBuildRadius())
                    .collect(java.util.stream.Collectors.toList());
            VillagerEntity preferred = chooseBuilder(community, profession, kind,
                    remembered.origin());
            if (preferred == null) continue;
            VillageBuildingProject project = new VillageBuildingProject(
                    remembered.center(), remembered.origin(), kind, profession,
                    id, rotation, placements, preferred.getUUID());
            project.index = first;
            data.begin(remembered.center(), remembered.origin(), kind.name(),
                    professionName(profession), id.toString(), rotation.name(),
                    first, preferred.getUUID());
            return project;
        }
        return null;
    }

    private static int firstRepairableMismatch(ServerWorld level,
                                               List<VillagePlacement> placements) {
        for (int index = 0; index < placements.size(); index++) {
            VillagePlacement placement = placements.get(index);
            if (!level.hasChunkAt(placement.pos())) continue;
            BlockState current = level.getBlockState(placement.pos());
            if (!current.equals(placement.state())
                    && VillageConstructionSafety.canApply(level,
                    placement.pos(), current, placement.state(),
                    placement.terrainReplacement(), placement.road())) {
                return index;
            }
        }
        return -1;
    }

    private static void refreshKnownStructures(ServerWorld level) {
        VillageEvolutionSavedData data = VillageEvolutionSavedData.get(level);
        for (BlockPos center : villageCenters(level)) {
            VillageBiomeStyle style = VillageBiomeStyle.at(level, center);
            if (VanillaVillageStructureCatalog.plans(level, style.id())
                    .isEmpty()) continue;
            List<RecognizedCandidate> candidates = new ArrayList<>();
            for (Anchor anchor : scanAnchors(level, center)) {
                RecognizedCandidate recognized = recognizeAtAnchor(level,
                        center, anchor, style.id());
                if (recognized != null) candidates.add(recognized);
            }
            candidates.sort(Comparator.comparingInt(
                    RecognizedCandidate::score).reversed());
            Set<String> acceptedKeys = new HashSet<>();
            List<IntBounds3D> acceptedBounds = new ArrayList<>();
            for (RecognizedCandidate recognized : candidates) {
                String key = recognized.template.id() + "|"
                        + recognized.rotated.rotation() + "|"
                        + recognized.origin.asLong();
                IntBounds3D candidateBounds = bounds(recognized.origin,
                        recognized.rotated);
                if (!acceptedKeys.add(key)
                        || acceptedBounds.stream().anyMatch(
                        candidateBounds::intersects)
                        || overlapsKnownStructure(level, candidateBounds)) {
                    continue;
                }
                acceptedBounds.add(candidateBounds);
                VillagerProfession profession = professionFor(
                        recognized.template);
                BuildingKind kind = kindFor(recognized.template, profession);
                data.rememberStructure(center, recognized.origin, kind.name(),
                        professionName(profession),
                        recognized.template.id().toString(),
                        recognized.rotated.rotation().name());
            }
        }
    }

    private static RecognizedCandidate recognizeAtAnchor(ServerWorld level,
            BlockPos center, Anchor anchor, String style) {
        RecognizedCandidate best = null;
        Set<String> checked = new HashSet<>();
        List<VanillaVillageStructureCatalog.TemplatePlan> templates =
                VanillaVillageStructureCatalog.plansContaining(level, style,
                        anchor.block);
        for (VanillaVillageStructureCatalog.TemplatePlan template : templates) {
            for (Rotation rotation : ROTATIONS) {
                VanillaVillageStructureCatalog.RotatedPlan rotated =
                        VanillaVillageStructureCatalog.rotate(template, rotation);
                if (rotated == null || rotated.blocks().isEmpty()) continue;
                for (VanillaVillageStructureCatalog.TemplateBlock block
                        : rotated.blocks()) {
                    if (block.state().getBlock() != anchor.block) continue;
                    BlockPos origin = anchor.pos.offset(
                            -block.relative().getX(),
                            -block.relative().getY(),
                            -block.relative().getZ());
                    if (origin.distSqr(center)
                            > (RuntimeConfig.snapshot().villageBuildRadius() + 16.0D)
                            * (RuntimeConfig.snapshot().villageBuildRadius() + 16.0D)) {
                        continue;
                    }
                    String key = template.id() + "|" + rotation + "|"
                            + origin.asLong();
                    if (!checked.add(key)) continue;
                    RecognizedCandidate candidate = evaluateRecognition(level,
                            origin, template, rotated);
                    if (candidate != null && (best == null
                            || candidate.score > best.score)) {
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private static RecognizedCandidate evaluateRecognition(ServerWorld level,
            BlockPos origin,
            VanillaVillageStructureCatalog.TemplatePlan template,
            VanillaVillageStructureCatalog.RotatedPlan rotated) {
        int solid = 0;
        int matches = 0;
        int repairable = 0;
        int conflicts = 0;
        for (VanillaVillageStructureCatalog.TemplateBlock expected
                : rotated.blocks()) {
            if (!expected.state().isAir()) solid++;
            BlockPos position = origin.offset(expected.relative());
            if (!level.hasChunkAt(position)) return null;
            BlockState current = level.getBlockState(position);
            if (current.equals(expected.state())
                    || current.getBlock() == expected.state().getBlock()) {
                if (!expected.state().isAir()) matches++;
            } else if (VillageConstructionSafety.canApply(level, position,
                    current, expected.state(),
                    expected.relative().getY() == 0, false)) {
                repairable++;
            } else {
                conflicts++;
            }
        }
        int minimumMatches = Math.max(8, Math.min(32, solid / 3));
        if (matches < minimumMatches || matches * 4 < Math.max(1, solid)
                || conflicts > Math.max(3, matches / 8)) {
            return null;
        }
        int score = matches * 8 - repairable - conflicts * 24;
        return new RecognizedCandidate(origin, template, rotated, score);
    }

    /** Identité persistante. */
    private static boolean overlapsKnownStructure(ServerWorld level,
                                                   IntBounds3D candidate) {
        for (VillageEvolutionSavedData.KnownStructure remembered
                : VillageEvolutionSavedData.get(level).knownStructures()) {
            ResourceLocation id = parseId(remembered.templateId());
            VanillaVillageStructureCatalog.TemplatePlan template =
                    VanillaVillageStructureCatalog.plan(level, id);
            if (template == null) continue;
            VanillaVillageStructureCatalog.RotatedPlan rotated =
                    VanillaVillageStructureCatalog.rotate(template,
                            parseRotation(remembered.rotation()));
            if (rotated != null && candidate.intersects(
                    bounds(remembered.origin(), rotated))) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlapsDifferentKnownStructure(ServerWorld level,
            BlockPos origin,
            VanillaVillageStructureCatalog.TemplatePlan template,
            Rotation rotation, String ownKey) {
        VanillaVillageStructureCatalog.RotatedPlan candidate =
                VanillaVillageStructureCatalog.rotate(template, rotation);
        if (candidate == null) return true;
        IntBounds3D bounds = bounds(origin, candidate);
        for (VillageEvolutionSavedData.KnownStructure remembered
                : VillageEvolutionSavedData.get(level).knownStructures()) {
            if (remembered.key().equals(ownKey)) continue;
            ResourceLocation otherId = parseId(remembered.templateId());
            VanillaVillageStructureCatalog.TemplatePlan other =
                    VanillaVillageStructureCatalog.plan(level, otherId);
            if (other == null) continue;
            VanillaVillageStructureCatalog.RotatedPlan rotatedOther =
                    VanillaVillageStructureCatalog.rotate(other,
                            parseRotation(remembered.rotation()));
            if (rotatedOther != null && bounds.intersects(
                    bounds(remembered.origin(), rotatedOther))) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlapsKnownFootprint(ServerWorld level,
            BlockPos origin,
            VanillaVillageStructureCatalog.TemplatePlan template,
            Rotation rotation) {
        VanillaVillageStructureCatalog.RotatedPlan candidate =
                VanillaVillageStructureCatalog.rotate(template, rotation);
        if (candidate == null) return true;
        IntBounds3D candidateBounds = bounds(origin, candidate);
        for (VillageEvolutionSavedData.KnownStructure remembered
                : VillageEvolutionSavedData.get(level).knownStructures()) {
            VanillaVillageStructureCatalog.TemplatePlan other =
                    VanillaVillageStructureCatalog.plan(level,
                            parseId(remembered.templateId()));
            if (other == null) continue;
            VanillaVillageStructureCatalog.RotatedPlan rotatedOther =
                    VanillaVillageStructureCatalog.rotate(other,
                            parseRotation(remembered.rotation()));
            if (rotatedOther != null && candidateBounds.horizontalIntersects(
                    bounds(remembered.origin(), rotatedOther))) {
                return true;
            }
        }
        return false;
    }

    private static IntBounds3D bounds(BlockPos origin,
            VanillaVillageStructureCatalog.RotatedPlan plan) {
        return new IntBounds3D(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + Math.max(0, plan.width() - 1),
                origin.getY() + Math.max(0, plan.height() - 1),
                origin.getZ() + Math.max(0, plan.depth() - 1));
    }

    private static VillageBuildingProject discoverProject(ServerWorld level) {
        List<VillagerEntity> villagers = loadedVillagers(level);
        if (villagers.size() < 2) return null;
        VillageBuildingProject remembered = discoverRememberedProject(level,
                villagers);
        if (remembered != null) return remembered;

        boolean incompleteAllowed = allow(level,
                VanillaInstinctsWorkLimiter.Task.INCOMPLETE_DISCOVERY,
                level.getGameTime(),
                RuntimeConfig.interval(FeatureFlag.VILLAGE_CONSTRUCTION,
                VillageConstructionRules.VILLAGE_INCOMPLETE_SCAN_TICKS),
                RuntimeConfig.interval(FeatureFlag.VILLAGE_CONSTRUCTION,
                VillageConstructionRules.VILLAGE_INCOMPLETE_REALTIME_MILLIS));
        Set<Long> checkedCommunities = new HashSet<>();
        for (VillagerEntity seed : villagers) {
            if (seed.isBaby()) continue;
            List<VillagerEntity> community = villagers.stream()
                    .filter(other -> other.distanceToSqr(seed)
                            <= RuntimeConfig.snapshot().villageBuildRadius()
                            * RuntimeConfig.snapshot().villageBuildRadius())
                    .collect(java.util.stream.Collectors.toList());
            if (community.size() < 2) continue;
            BlockPos center = communityCenter(community);
            if (!checkedCommunities.add(
                    VillageEvolutionSavedData.growthKey(center))) {
                continue;
            }

            VillageBuildingProject growth = discoverGrowthProject(level, center,
                    community);
            if (growth != null) return growth;
            if (!incompleteAllowed) continue;
            incompleteAllowed = false;

            ProjectSeed incomplete = discoverIncompleteVanilla(level, center);
            VillageBuildingProject project = startIncompleteProject(level, center,
                    community, incomplete);
            if (project != null) return project;
        }
        return null;
    }

    private static VillageBuildingProject startIncompleteProject(
            ServerWorld level, BlockPos center, List<VillagerEntity> community,
            ProjectSeed seed) {
        if (seed == null) return null;
        VillagerEntity preferred = chooseBuilder(community, seed.profession,
                seed.kind, seed.origin);
        if (preferred == null) return null;

        VillageBuildingProject project = new VillageBuildingProject(center, seed.origin,
                seed.kind, seed.profession, seed.templateId, seed.rotation,
                seed.placements, preferred.getUUID());
        VillageEvolutionSavedData data = VillageEvolutionSavedData.get(level);
        data.rememberStructure(center, seed.origin, seed.kind.name(),
                professionName(seed.profession), seed.templateId.toString(),
                seed.rotation.name());
        data.begin(center, seed.origin, seed.kind.name(),
                professionName(seed.profession), seed.templateId.toString(),
                seed.rotation.name(), 0, preferred.getUUID());
        return project;
    }

    private static VillageBuildingProject discoverGrowthProject(
            ServerWorld level, BlockPos center, List<VillagerEntity> community) {
        VillageEvolutionSavedData data = VillageEvolutionSavedData.get(level);
        long day = Math.floorDiv(level.getDayTime(), 24_000L);
        long gameTime = level.getGameTime();
        if (!data.canGrow(center, day, gameTime)) return null;

        VillageBiomeStyle style = VillageBiomeStyle.at(level, center);
        List<VanillaVillageStructureCatalog.TemplatePlan> candidates =
                growthCandidates(level, center, community, style, day);
        if (candidates.isEmpty()) return null;
        int configuredSiteBudget = Math.max(8,
                RuntimeConfig.snapshot().villageSiteSearchLimit()
                        / candidates.size());
        int siteBudget = VanillaInstinctsScheduler.precisionLimit(level,
                configuredSiteBudget,
                PerformanceRules.MINIMUM_CANDIDATE_LIMIT);

        for (VanillaVillageStructureCatalog.TemplatePlan template
                : candidates) {
            VillagerProfession profession = professionFor(template);
            BuildingKind kind = kindFor(template, profession);
            GrowthSite site = findGrowthSite(level, center, template, day,
                    siteBudget);
            if (site == null) continue;
            List<VillagePlacement> placements = vanillaBlueprint(level, center,
                    site.origin, template, site.rotation, style, true);
            if (placements.isEmpty()) continue;

            VillagerEntity preferred = chooseBuilder(community, profession, kind,
                    site.origin);
            if (preferred == null) continue;
            VillageBuildingProject project = new VillageBuildingProject(center, site.origin,
                    kind, profession, template.id(), site.rotation,
                    placements, preferred.getUUID());
            data.markGrowth(center, day, gameTime);
            data.begin(center, site.origin, kind.name(),
                    professionName(profession), template.id().toString(),
                    site.rotation.name(), 0, preferred.getUUID());
            return project;
        }
        return null;
    }

    private static List<VanillaVillageStructureCatalog.TemplatePlan>
            growthCandidates(ServerWorld level, BlockPos center,
            List<VillagerEntity> community, VillageBiomeStyle style, long day) {
        List<VanillaVillageStructureCatalog.TemplatePlan> all =
                VanillaVillageStructureCatalog.plans(level, style.id());
        if (all.isEmpty()) return fr.vanillainstincts.compat.LegacyJava8.listOf();

        List<VillageEvolutionSavedData.KnownStructure> nearby =
                knownStructuresNear(level, center);
        Map<ResourceLocation, Integer> uses = new HashMap<>();
        for (VillageEvolutionSavedData.KnownStructure structure : nearby) {
            ResourceLocation id = parseId(structure.templateId());
            if (id != null) uses.merge(id, 1, Integer::sum);
        }

        int adults = (int) community.stream().filter(v -> !v.isBaby()).count();
        boolean housingNeeded = VillageGrowthPolicy.needsHousing(adults,
                housingCapacity(level, nearby));
        int[] counts = new int[all.size()];
        boolean[] housing = new boolean[all.size()];
        for (int index = 0; index < all.size(); index++) {
            VanillaVillageStructureCatalog.TemplatePlan plan = all.get(index);
            counts[index] = uses.getOrDefault(plan.id(), 0);
            housing[index] = plan.bedCount() > 0;
        }

        long selector = level.getSeed() ^ center.asLong() ^ day
                ^ ((long) nearby.size() << 32);
        List<Integer> order = VillageTemplateSelectionPolicy.order(counts,
                housing, housingNeeded, selector,
                RuntimeConfig.scaledCount(FeatureFlag.VILLAGE_CONSTRUCTION,
                        VillageConstructionRules.VILLAGE_GROWTH_TEMPLATE_ATTEMPTS));
        return order.stream().map(all::get).collect(java.util.stream.Collectors.toList());
    }

    private static int housingCapacity(ServerWorld level,
            List<VillageEvolutionSavedData.KnownStructure> structures) {
        int beds = 0;
        for (VillageEvolutionSavedData.KnownStructure structure : structures) {
            VanillaVillageStructureCatalog.TemplatePlan template =
                    VanillaVillageStructureCatalog.plan(level,
                            parseId(structure.templateId()));
            if (template != null) beds += template.bedCount();
        }
        return beds;
    }

    private static List<VillageEvolutionSavedData.KnownStructure>
            knownStructuresNear(ServerWorld level, BlockPos center) {
        double radius = RuntimeConfig.snapshot().villageBuildRadius() + 16.0D;
        double radiusSqr = radius * radius;
        return VillageEvolutionSavedData.get(level).knownStructures().stream()
                .filter(structure -> structure.origin().distSqr(center)
                        <= radiusSqr)
                .collect(java.util.stream.Collectors.toList());
    }

    private static GrowthSite findGrowthSite(ServerWorld level,
            BlockPos center,
            VanillaVillageStructureCatalog.TemplatePlan template,
            long day, int searchLimit) {
        int ringMaximum = Math.max(
                VillageConstructionRules.VILLAGE_SITE_RING_MIN,
                Math.min(RuntimeConfig.snapshot().villageBuildRadius(),
                        VillageConstructionRules.VILLAGE_SITE_RING_MAX));
        int ringCount = 1 + Math.max(0,
                (ringMaximum
                        - VillageConstructionRules.VILLAGE_SITE_RING_MIN) / 3);
        int seedShift = Math.floorMod((int) (level.getSeed()
                ^ center.asLong() ^ day ^ template.id().hashCode()), 24);
        for (int attempt = 0; attempt < searchLimit; attempt++) {
            int ring = attempt % ringCount;
            int round = attempt / ringCount;
            int radius = VillageConstructionRules.VILLAGE_SITE_RING_MIN + ring * 3;
            int step = Math.floorMod(seedShift + ring * 7 + round * 11, 24);
            double angle = Math.PI * 2.0D * step / 24.0D;
            int x = center.getX()
                    + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ()
                    + (int) Math.round(Math.sin(angle) * radius);
            int rotationIndex = Math.floorMod(step + radius + round,
                    ROTATIONS.length);
            Rotation rotation = ROTATIONS[rotationIndex];
            VanillaVillageStructureCatalog.RotatedPlan rotated =
                    VanillaVillageStructureCatalog.rotate(template, rotation);
            if (rotated == null || rotated.blocks().isEmpty()) continue;
            BlockPos origin = VillageConstructionSiteValidator.origin(
                    level, x, z, rotated);
            if (origin == null
                    || overlapsKnownFootprint(level, origin, template,
                    rotation)
                    || !VillageConstructionSiteValidator.isFree(level,
                    origin, rotated)) {
                continue;
            }
            return new GrowthSite(origin, rotation);
        }
        return null;
    }

    private static VillagerEntity chooseBuilder(List<VillagerEntity> community,
                                          VillagerProfession profession,
                                          BuildingKind kind,
                                          BlockPos origin) {
        boolean simpleHouse = kind == BuildingKind.HOUSE;
        return community.stream()
                .filter(v -> !v.isBaby() && !v.isTrading()
                        && VillageConstructionCapability.canWorkOn(
                        v.getVillagerData().getProfession(), profession,
                        false, simpleHouse))
                .min(Comparator.<VillagerEntity>comparingInt(v ->
                        VillageConstructionCapability.workPreference(
                                v.getVillagerData().getProfession(),
                                profession, false, simpleHouse))
                        .thenComparingDouble(v ->
                                v.blockPosition().distSqr(origin)))
                .orElse(null);
    }

    private static ProjectSeed discoverIncompleteVanilla(ServerWorld level,
                                                         BlockPos center) {
        VillageBiomeStyle style = VillageBiomeStyle.at(level, center);
        if (VanillaVillageStructureCatalog.plans(level, style.id()).isEmpty()) {
            return null;
        }
        List<Anchor> anchors = scanAnchors(level, center);
        if (anchors.isEmpty()) return null;

        IncompleteCandidate best = null;
        Set<String> checked = new HashSet<>();
        for (Anchor anchor : anchors) {
            List<VanillaVillageStructureCatalog.TemplatePlan> templates =
                    VanillaVillageStructureCatalog.plansContaining(level,
                            style.id(), anchor.block);
            for (VanillaVillageStructureCatalog.TemplatePlan template
                    : templates) {
                for (Rotation rotation : ROTATIONS) {
                    VanillaVillageStructureCatalog.RotatedPlan rotated =
                            VanillaVillageStructureCatalog.rotate(template,
                                    rotation);
                    if (rotated == null || rotated.blocks().isEmpty()) continue;
                    for (VanillaVillageStructureCatalog.TemplateBlock block
                            : rotated.blocks()) {
                        if (block.state().getBlock() != anchor.block) continue;
                        BlockPos origin = anchor.pos.offset(
                                -block.relative().getX(),
                                -block.relative().getY(),
                                -block.relative().getZ());
                        double radius = RuntimeConfig.snapshot().villageBuildRadius()
                                + 16.0D;
                        if (origin.distSqr(center) > radius * radius) continue;
                        String key = template.id() + "|" + rotation + "|"
                                + origin.asLong();
                        if (!checked.add(key)
                                || overlapsDifferentKnownStructure(level,
                                origin, template, rotation, key)) continue;
                        IncompleteCandidate candidate = evaluateIncomplete(
                                level, origin, template, rotated);
                        if (candidate != null && (best == null
                                || candidate.score > best.score)) {
                            best = candidate;
                        }
                    }
                }
            }
        }
        if (best == null) return null;
        VillagerProfession profession = professionFor(best.template);
        BuildingKind kind = kindFor(best.template, profession);
        List<VillagePlacement> completeBlueprint = vanillaBlueprint(level, center,
                best.origin, best.template, best.rotated.rotation(), style,
                true);
        if (completeBlueprint.isEmpty()) return null;
        return new ProjectSeed(best.origin, kind, profession,
                best.template.id(), best.rotated.rotation(),
                fr.vanillainstincts.compat.LegacyJava8.copyList(completeBlueprint));
    }

    private static IncompleteCandidate evaluateIncomplete(ServerWorld level,
            BlockPos origin,
            VanillaVillageStructureCatalog.TemplatePlan template,
            VanillaVillageStructureCatalog.RotatedPlan rotated) {
        int matches = 0;
        int repairs = 0;
        int conflicts = 0;
        List<VillagePlacement> placements = new ArrayList<>();
        for (VanillaVillageStructureCatalog.TemplateBlock expected
                : rotated.blocks()) {
            BlockPos worldPos = origin.offset(expected.relative());
            if (!level.hasChunkAt(worldPos)) return null;
            BlockState current = level.getBlockState(worldPos);
            boolean terrain = expected.relative().getY() == 0;
            if (current.equals(expected.state())) {
                if (!expected.state().isAir()) matches++;
                placements.add(new VillagePlacement(worldPos, expected.state(),
                        terrain, false));
            } else if (current.getBlock() == expected.state().getBlock()) {
                if (!expected.state().isAir()) matches++;
                repairs++;
                placements.add(new VillagePlacement(worldPos, expected.state(),
                        terrain, false));
            } else if (VillageConstructionSafety.canApply(level, worldPos,
                    current, expected.state(), terrain, false)) {
                repairs++;
                placements.add(new VillagePlacement(worldPos, expected.state(),
                        terrain, false));
            } else {
                conflicts++;
                // Audit
                placements.add(new VillagePlacement(worldPos, expected.state(),
                        terrain, false));
            }
        }
        if (matches < 8 || repairs == 0
                || conflicts > Math.max(3, matches / 8)) return null;
        int score = matches * 6 - repairs - conflicts * 24;
        return new IncompleteCandidate(origin, template, rotated,
                fr.vanillainstincts.compat.LegacyJava8.copyList(placements), score);
    }

    private static List<Anchor> scanAnchors(ServerWorld level,
                                            BlockPos center) {
        Map<Long, Anchor> result = new LinkedHashMap<>();
        double radius = RuntimeConfig.snapshot().villageBuildRadius() + 16.0D;
        double radiusSqr = radius * radius;
        for (VillagerEntity villager : loadedVillagers(level)) {
            if (villager.blockPosition().distSqr(center) > radiusSqr) continue;
            addMemoryAnchor(level, villager, MemoryModuleType.HOME, result);
            addMemoryAnchor(level, villager, MemoryModuleType.JOB_SITE, result);
            if (result.size() >= 192) break;
        }
        if (result.isEmpty()) scanLocalAnchors(level, center, result);
        return fr.vanillainstincts.compat.LegacyJava8.copyList(result.values());
    }

    private static void addMemoryAnchor(ServerWorld level, VillagerEntity villager,
            MemoryModuleType<GlobalPos> memory, Map<Long, Anchor> result) {
        GlobalPos global = villager.getBrain().getMemory(memory).orElse(null);
        if (global == null || !global.dimension().equals(level.dimension())) {
            return;
        }
        BlockPos position = global.pos();
        if (!level.hasChunkAt(position)) return;
        Block block = level.getBlockState(position).getBlock();
        if (block instanceof BedBlock || isWorkstation(block)) {
            result.putIfAbsent(position.asLong(),
                    new Anchor(position.immutable(), block));
        }
    }

    private static void scanLocalAnchors(ServerWorld level, BlockPos center,
                                         Map<Long, Anchor> result) {
        int radius = 12;
        for (int y = -4; y <= 6 && result.size() < 64; y++) {
            for (int x = -radius; x <= radius && result.size() < 64; x++) {
                for (int z = -radius; z <= radius && result.size() < 64; z++) {
                    BlockPos position = center.offset(x, y, z);
                    if (!level.hasChunkAt(position)) continue;
                    Block block = level.getBlockState(position).getBlock();
                    if (block instanceof BedBlock || isWorkstation(block)) {
                        result.putIfAbsent(position.asLong(),
                                new Anchor(position.immutable(), block));
                    }
                }
            }
        }
    }

    private static VanillaVillageStructureCatalog.TemplatePlan selectTemplate(
            ServerWorld level, BlockPos center, BuildingRequest request,
            VillageBiomeStyle style) {
        List<VanillaVillageStructureCatalog.TemplatePlan> all =
                VanillaVillageStructureCatalog.plans(level, style.id());
        List<VanillaVillageStructureCatalog.TemplatePlan> candidates = all.stream()
                .filter(plan -> fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((request.kind)) { case HOUSE:  return hasBed(plan)
                            && professionFor(plan) == VillagerProfession.NONE; case WORKSHOP:  return professionFor(plan)
                            == request.profession; case COMMUNITY:  return !hasBed(plan)
                            && professionFor(plan) == VillagerProfession.NONE;  default: throw new AssertionError("Unexpected switch value"); } }))
                .collect(java.util.stream.Collectors.toList());
        if (candidates.isEmpty()) return null;
        long day = Math.floorDiv(level.getDayTime(), 24_000L);
        long selector = level.getSeed() ^ center.asLong() ^ day
                ^ professionName(request.profession).hashCode();
        int index = Math.floorMod((int) (selector ^ (selector >>> 32)),
                candidates.size());
        return candidates.get(index);
    }

    private static boolean hasBed(
            VanillaVillageStructureCatalog.TemplatePlan plan) {
        return VillageTemplateClassifier.hasBed(plan);
    }

    private static BuildingKind kindFor(
            VanillaVillageStructureCatalog.TemplatePlan plan,
            VillagerProfession profession) {
        return VillageTemplateClassifier.kindFor(plan, profession);
    }

    private static VillagerProfession professionFor(
            VanillaVillageStructureCatalog.TemplatePlan plan) {
        return VillageTemplateClassifier.professionFor(plan);
    }

    public static VillagerProfession professionFromTemplateName(
            ResourceLocation id) {
        return VillageTemplateClassifier.professionFromTemplateName(id);
    }

    private static List<VillagePlacement> vanillaBlueprint(ServerWorld level,
                                                     BlockPos center,
                                                     BlockPos origin,
            VanillaVillageStructureCatalog.TemplatePlan template,
                                                     Rotation rotation,
                                                     VillageBiomeStyle style,
                                                     boolean addRoad) {
        VanillaVillageStructureCatalog.RotatedPlan rotated =
                VanillaVillageStructureCatalog.rotate(template, rotation);
        if (rotated == null || rotated.blocks().isEmpty()) return fr.vanillainstincts.compat.LegacyJava8.listOf();
        LinkedHashMap<Long, VillagePlacement> result = new LinkedHashMap<>();
        for (VanillaVillageStructureCatalog.TemplateBlock block
                : rotated.blocks()) {
            BlockPos position = origin.offset(block.relative());
            put(result, position, block.state(),
                    block.relative().getY() == 0, false, block.lootTable(),
                    block.lootTableSeed());
        }
        if (addRoad) {
            BlockPos buildingCenter = origin.offset(rotated.width() / 2, 0,
                    rotated.depth() / 2);
            Direction front = horizontalDirection(buildingCenter, center);
            BlockPos entrance = edgeOutside(origin, rotated, front);
            addPath(level, result, center, entrance, style);
        }
        return new ArrayList<>(result.values());
    }

    private static BlockPos edgeOutside(BlockPos origin,
            VanillaVillageStructureCatalog.RotatedPlan plan,
                                        Direction towardVillage) {
        int middleX = plan.width() / 2;
        int middleZ = plan.depth() / 2;
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((towardVillage)) { case NORTH:  return origin.offset(middleX, 0, -1); case SOUTH:  return origin.offset(middleX, 0, plan.depth()); case WEST:  return origin.offset(-1, 0, middleZ); case EAST:  return origin.offset(plan.width(), 0, middleZ); default:  return origin.offset(middleX, 0, -1); } });
    }

    private static void addPath(ServerWorld level,
                                Map<Long, VillagePlacement> result,
                                BlockPos start, BlockPos end,
                                VillageBiomeStyle style) {
        BlockPos anchor = VillageRoadPlanner.nearestExistingPath(level, start,
                24);
        BlockPos startHint = anchor == null ? start : anchor;
        List<BlockPos> centerline = VillageRoadPlanner.plan(level, startHint,
                end.above(), VillageConstructionRules.VILLAGE_ROAD_MAX_NODES);
        List<BlockPos> road = VillageRoadPlanner.widen(level, centerline,
                VillageConstructionRules.VILLAGE_ROAD_WIDTH);
        for (BlockPos feet : road) {
            BlockPos ground = feet.below();
            if (level.hasChunkAt(ground)) {
                put(result, ground, style.path().defaultBlockState(), true,
                        true);
            }
        }
    }

    private static void captureNightSnapshots(ServerWorld level,
                                              VillageEvolutionRuntime state, long day) {
        state.snapshots.clear();
        for (BlockPos center : villageCenters(level)) {
            LinkedHashMap<BlockPos, VillageSnapshotBlock> roads =
                    new LinkedHashMap<>();
            int radius = VillageConstructionRules.VILLAGE_NIGHT_SNAPSHOT_RADIUS;
            BlockPos.betweenClosedStream(center.offset(-radius, -5, -radius),
                            center.offset(radius, 5, radius))
                    .filter(level::hasChunkAt)
                    .forEach(pos -> {
                        if (roads.size() >= 2_048) return;
                        BlockState blockState = level.getBlockState(pos);
                        if (isRoadPosition(level, pos, blockState)) {
                            roads.put(pos.immutable(), new VillageSnapshotBlock(
                                    blockState, VillagerProfession.NONE, true));
                        }
                    });
            state.snapshots.put(center.asLong(), new VillageRepairSnapshot(center,
                    roads));
        }
        state.snapshotDay = day;
    }

    private static void prepareRepairs(ServerWorld level, VillageEvolutionRuntime state,
                                       long day) {
        state.repairs.clear();
        int limit = VillageConstructionRules.VILLAGE_REPAIR_LIMIT;
        VillageEvolutionSavedData data = VillageEvolutionSavedData.get(level);

        // Modèles
        for (VillageEvolutionSavedData.KnownStructure remembered
                : data.knownStructures()) {
            if (state.repairs.size() >= limit) break;
            ResourceLocation id = parseId(remembered.templateId());
            VanillaVillageStructureCatalog.TemplatePlan template =
                    VanillaVillageStructureCatalog.plan(level, id);
            if (template == null) {
                data.forgetStructure(remembered);
                continue;
            }
            VanillaVillageStructureCatalog.RotatedPlan rotated =
                    VanillaVillageStructureCatalog.rotate(template,
                            parseRotation(remembered.rotation()));
            if (rotated == null) continue;
            VillagerProfession profession = profession(remembered.profession());
            for (VanillaVillageStructureCatalog.TemplateBlock expected
                    : rotated.blocks()) {
                if (state.repairs.size() >= limit) break;
                BlockPos position = remembered.origin().offset(
                        expected.relative());
                if (!level.hasChunkAt(position)) continue;
                BlockState current = level.getBlockState(position);
                boolean terrain = expected.relative().getY() == 0;
                if (!current.equals(expected.state())
                        && VillageConstructionSafety.canApply(level, position,
                        current, expected.state(), terrain, false)) {
                    state.repairs.add(new VillageRepairTask(position,
                            expected.state(), profession, terrain, false,
                            null));
                }
            }
        }

        // Chemins
        for (VillageRepairSnapshot snapshot : state.snapshots.values()) {
            for (Map.Entry<BlockPos, VillageSnapshotBlock> expected
                    : snapshot.blocks().entrySet()) {
                if (state.repairs.size() >= limit) break;
                if (!level.hasChunkAt(expected.getKey())) continue;
                BlockState current = level.getBlockState(expected.getKey());
                VillageSnapshotBlock remembered = expected.getValue();
                if (!current.equals(remembered.state())
                        && VillageConstructionSafety.canApply(level,
                        expected.getKey(), current, remembered.state(), true,
                        true)) {
                    state.repairs.add(new VillageRepairTask(expected.getKey(),
                            remembered.state(), VillagerProfession.NONE, true,
                            true, null));
                }
            }
        }
        state.repairPreparedDay = day;
    }

    private static VillagerProfession professionForWorkstation(Block block) {
        return VillageTemplateClassifier.professionForWorkstation(block);
    }

    private static boolean isRoadPosition(ServerWorld level, BlockPos pos,
                                          BlockState state) {
        if (state.is(Blocks.GRASS_PATH)) return true;
        if (!state.is(Blocks.SMOOTH_SANDSTONE)) return false;
        BlockState above = level.getBlockState(pos.above());
        return above.isAir() || above.getMaterial().isReplaceable();
    }

    private static boolean isNaturalGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                || state.is(Blocks.DIRT) || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW)
                || state.is(Blocks.STONE) || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE);
    }

    private static boolean isMissing(BlockState state) {
        return state.isAir() || state.is(Blocks.FIRE)
                || !state.getFluidState().isEmpty();
    }

    public static boolean isVillageArchitecture(BlockState state) {
        if (state == null || state.isAir()) return false;
        String path = Registry.BLOCK.getKey(state.getBlock()).getPath()
                .toLowerCase(Locale.ROOT);
        return containsAny(path, "planks", "log", "wood", "cobblestone",
                "stone_brick", "sandstone", "terracotta", "brick",
                "glass", "door", "fence", "wall", "stairs", "slab",
                "torch", "lantern", "bell", "bed", "composter",
                "barrel", "smoker", "furnace", "grindstone", "lectern",
                "loom", "cartography_table", "fletching_table",
                "brewing_stand", "smithing_table", "stonecutter",
                "cauldron", "hay_block", "dirt_path");
    }

    private static List<BlockPos> villageCenters(ServerWorld level) {
        List<VillagerEntity> villagers = loadedVillagers(level);
        Map<Long, List<VillagerEntity>> cells = new HashMap<>();
        for (VillagerEntity villager : villagers) {
            long key = (((long) (villager.blockPosition().getX() >> 6)) << 32)
                    ^ ((villager.blockPosition().getZ() >> 6) & 0xffffffffL);
            cells.computeIfAbsent(key, ignored -> new ArrayList<>()).add(villager);
        }
        return cells.values().stream().filter(group -> group.size() >= 2)
                .map(VillageEvolutionController::communityCenter).collect(java.util.stream.Collectors.toList());
    }

    private static List<VillagerEntity> loadedVillagers(ServerWorld level) {
        List<VillagerEntity> villagers = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof VillagerEntity && ((VillagerEntity) (entity)).isAlive()) { VillagerEntity villager = (VillagerEntity) (entity); 
                villagers.add(villager);
            }
        }
        return villagers;
    }

    private static BlockPos communityCenter(List<VillagerEntity> villagers) {
        long x = 0L, y = 0L, z = 0L;
        for (VillagerEntity villager : villagers) {
            x += villager.blockPosition().getX();
            y += villager.blockPosition().getY();
            z += villager.blockPosition().getZ();
        }
        int size = Math.max(1, villagers.size());
        return new BlockPos((int) (x / size), (int) (y / size),
                (int) (z / size));
    }

    private static VillagerEntity builder(ServerWorld level, UUID preferred,
                                    BlockPos target,
                                    VillagerProfession profession,
                                    boolean road,
                                    boolean simpleHouse) {
        double radiusSqr = (RuntimeConfig.snapshot().villageBuildRadius() * 2.0D)
                * (RuntimeConfig.snapshot().villageBuildRadius() * 2.0D);
        Entity entity = preferred == null ? null : level.getEntity(preferred);
        if (entity instanceof VillagerEntity && ((VillagerEntity) (entity)).isAlive()
                && !((VillagerEntity) (entity)).isBaby() && !((VillagerEntity) (entity)).isTrading()
                && ((VillagerEntity) (entity)).blockPosition().distSqr(target) <= radiusSqr
                && VillageConstructionCapability.canWorkOn(
                ((VillagerEntity) (entity)).getVillagerData().getProfession(), profession,
                road, simpleHouse)) { VillagerEntity villager = (VillagerEntity) (entity); 
            return villager;
        }
        return loadedVillagers(level).stream()
                .filter(v -> !v.isBaby() && !v.isTrading()
                        && v.blockPosition().distSqr(target) <= radiusSqr
                        && VillageConstructionCapability.canWorkOn(
                        v.getVillagerData().getProfession(), profession,
                        road, simpleHouse))
                .min(Comparator.<VillagerEntity>comparingInt(v ->
                        VillageConstructionCapability.workPreference(
                                v.getVillagerData().getProfession(),
                                profession, road, simpleHouse))
                        .thenComparingDouble(v ->
                                v.blockPosition().distSqr(target)))
                .orElse(null);
    }

    static String professionName(VillagerProfession profession) {
        if (profession == null || profession == VillagerProfession.NONE)
            return "none";
        if (profession == VillagerProfession.ARMORER) return "armorer";
        if (profession == VillagerProfession.TOOLSMITH) return "toolsmith";
        if (profession == VillagerProfession.WEAPONSMITH) return "weaponsmith";
        if (profession == VillagerProfession.CLERIC) return "cleric";
        if (profession == VillagerProfession.FISHERMAN) return "fisherman";
        if (profession == VillagerProfession.FARMER) return "farmer";
        if (profession == VillagerProfession.LIBRARIAN) return "librarian";
        if (profession == VillagerProfession.CARTOGRAPHER) return "cartographer";
        if (profession == VillagerProfession.BUTCHER) return "butcher";
        if (profession == VillagerProfession.FLETCHER) return "fletcher";
        if (profession == VillagerProfession.SHEPHERD) return "shepherd";
        if (profession == VillagerProfession.LEATHERWORKER) return "leatherworker";
        if (profession == VillagerProfession.MASON) return "mason";
        return "none";
    }

    static VillagerProfession profession(String name) {
        if (name == null) return VillagerProfession.NONE;
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((name)) { case "armorer":  return VillagerProfession.ARMORER; case "toolsmith":  return VillagerProfession.TOOLSMITH; case "weaponsmith":  return VillagerProfession.WEAPONSMITH; case "cleric":  return VillagerProfession.CLERIC; case "fisherman":  return VillagerProfession.FISHERMAN; case "farmer":  return VillagerProfession.FARMER; case "librarian":  return VillagerProfession.LIBRARIAN; case "cartographer":  return VillagerProfession.CARTOGRAPHER; case "butcher":  return VillagerProfession.BUTCHER; case "fletcher":  return VillagerProfession.FLETCHER; case "shepherd":  return VillagerProfession.SHEPHERD; case "leatherworker":  return VillagerProfession.LEATHERWORKER; case "mason":  return VillagerProfession.MASON; default:  return VillagerProfession.NONE; } });
    }

    private static boolean isWorkstation(Block block) {
        return VillageTemplateClassifier.isWorkstation(block);
    }

    private static Direction horizontalDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) return dx >= 0
                ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static ResourceLocation parseId(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        int separator = value.indexOf(':');
        try {
            return separator < 0
                    ? new ResourceLocation("minecraft", value)
                    : new ResourceLocation(
                    value.substring(0, separator),
                    value.substring(separator + 1));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Rotation parseRotation(String value) {
        if (value == null || value.trim().isEmpty()) return Rotation.NONE;
        try {
            return Rotation.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return Rotation.NONE;
        }
    }

    private static void put(Map<Long, VillagePlacement> placements, BlockPos pos,
                            BlockState state) {
        put(placements, pos, state, false, false);
    }

    private static void put(Map<Long, VillagePlacement> placements, BlockPos pos,
                            BlockState state, boolean terrainReplacement) {
        put(placements, pos, state, terrainReplacement, false);
    }

    private static void put(Map<Long, VillagePlacement> placements, BlockPos pos,
                            BlockState state, boolean terrainReplacement,
                            boolean road) {
        placements.put(pos.asLong(), new VillagePlacement(pos.immutable(), state,
                terrainReplacement, road));
    }

    private static void put(Map<Long, VillagePlacement> placements, BlockPos pos,
                            BlockState state, boolean terrainReplacement,
                            boolean road, ResourceLocation lootTable,
                            long lootTableSeed) {
        placements.put(pos.asLong(), new VillagePlacement(pos.immutable(), state,
                terrainReplacement, road, lootTable, lootTableSeed));
    }

    private static void clearBuilderHand(ServerWorld level, UUID id) {
        Entity entity = id == null ? null : level.getEntity(id);
        if (entity instanceof VillagerEntity) { VillagerEntity villager = (VillagerEntity) (entity); 
            villager.setItemInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static VillageEvolutionRuntime state(ServerWorld level) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(level, ignored -> new VillageEvolutionRuntime());
        }
    }

    public enum BuildingKind { HOUSE, WORKSHOP, COMMUNITY }

    private static class BuildingRequest {
        private final BuildingKind kind;
        private final VillagerProfession profession;

        public BuildingRequest(BuildingKind kind, VillagerProfession profession) {
            this.kind = kind;
            this.profession = profession;
        }

        public BuildingKind kind() { return this.kind; }

        public VillagerProfession profession() { return this.profession; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BuildingRequest)) return false;
            BuildingRequest that = (BuildingRequest) other;
            return java.util.Objects.equals(this.kind, that.kind) && java.util.Objects.equals(this.profession, that.profession);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.kind, this.profession); }

        @Override
        public String toString() {
            return "BuildingRequest[" + "kind=" + this.kind + ", " + "profession=" + this.profession + "]";
        }

    }

    private static class Anchor {
        private final BlockPos pos;
        private final Block block;

        public Anchor(BlockPos pos, Block block) {
            this.pos = pos;
            this.block = block;
        }

        public BlockPos pos() { return this.pos; }

        public Block block() { return this.block; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Anchor)) return false;
            Anchor that = (Anchor) other;
            return java.util.Objects.equals(this.pos, that.pos) && java.util.Objects.equals(this.block, that.block);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.pos, this.block); }

        @Override
        public String toString() {
            return "Anchor[" + "pos=" + this.pos + ", " + "block=" + this.block + "]";
        }

    }

    private static class ProjectSeed {
        private final BlockPos origin;
        private final BuildingKind kind;
        private final VillagerProfession profession;
        private final ResourceLocation templateId;
        private final Rotation rotation;
        private final List<VillagePlacement> placements;

        public ProjectSeed(BlockPos origin, BuildingKind kind, VillagerProfession profession, ResourceLocation templateId, Rotation rotation, List<VillagePlacement> placements) {
            this.origin = origin;
            this.kind = kind;
            this.profession = profession;
            this.templateId = templateId;
            this.rotation = rotation;
            this.placements = placements;
        }

        public BlockPos origin() { return this.origin; }

        public BuildingKind kind() { return this.kind; }

        public VillagerProfession profession() { return this.profession; }

        public ResourceLocation templateId() { return this.templateId; }

        public Rotation rotation() { return this.rotation; }

        public List<VillagePlacement> placements() { return this.placements; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ProjectSeed)) return false;
            ProjectSeed that = (ProjectSeed) other;
            return java.util.Objects.equals(this.origin, that.origin) && java.util.Objects.equals(this.kind, that.kind) && java.util.Objects.equals(this.profession, that.profession) && java.util.Objects.equals(this.templateId, that.templateId) && java.util.Objects.equals(this.rotation, that.rotation) && java.util.Objects.equals(this.placements, that.placements);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.origin, this.kind, this.profession, this.templateId, this.rotation, this.placements); }

        @Override
        public String toString() {
            return "ProjectSeed[" + "origin=" + this.origin + ", " + "kind=" + this.kind + ", " + "profession=" + this.profession + ", " + "templateId=" + this.templateId + ", " + "rotation=" + this.rotation + ", " + "placements=" + this.placements + "]";
        }

    }

    private static class IncompleteCandidate {
        private final BlockPos origin;
        private final VanillaVillageStructureCatalog.TemplatePlan template;
        private final VanillaVillageStructureCatalog.RotatedPlan rotated;
        private final List<VillagePlacement> placements;
        private final int score;

        public IncompleteCandidate(BlockPos origin, VanillaVillageStructureCatalog.TemplatePlan template, VanillaVillageStructureCatalog.RotatedPlan rotated, List<VillagePlacement> placements, int score) {
            this.origin = origin;
            this.template = template;
            this.rotated = rotated;
            this.placements = placements;
            this.score = score;
        }

        public BlockPos origin() { return this.origin; }

        public VanillaVillageStructureCatalog.TemplatePlan template() { return this.template; }

        public VanillaVillageStructureCatalog.RotatedPlan rotated() { return this.rotated; }

        public List<VillagePlacement> placements() { return this.placements; }

        public int score() { return this.score; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IncompleteCandidate)) return false;
            IncompleteCandidate that = (IncompleteCandidate) other;
            return java.util.Objects.equals(this.origin, that.origin) && java.util.Objects.equals(this.template, that.template) && java.util.Objects.equals(this.rotated, that.rotated) && java.util.Objects.equals(this.placements, that.placements) && this.score == that.score;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.origin, this.template, this.rotated, this.placements, this.score); }

        @Override
        public String toString() {
            return "IncompleteCandidate[" + "origin=" + this.origin + ", " + "template=" + this.template + ", " + "rotated=" + this.rotated + ", " + "placements=" + this.placements + ", " + "score=" + this.score + "]";
        }

    }

    private static class RecognizedCandidate {
        private final BlockPos origin;
        private final VanillaVillageStructureCatalog.TemplatePlan template;
        private final VanillaVillageStructureCatalog.RotatedPlan rotated;
        private final int score;

        public RecognizedCandidate(BlockPos origin, VanillaVillageStructureCatalog.TemplatePlan template, VanillaVillageStructureCatalog.RotatedPlan rotated, int score) {
            this.origin = origin;
            this.template = template;
            this.rotated = rotated;
            this.score = score;
        }

        public BlockPos origin() { return this.origin; }

        public VanillaVillageStructureCatalog.TemplatePlan template() { return this.template; }

        public VanillaVillageStructureCatalog.RotatedPlan rotated() { return this.rotated; }

        public int score() { return this.score; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RecognizedCandidate)) return false;
            RecognizedCandidate that = (RecognizedCandidate) other;
            return java.util.Objects.equals(this.origin, that.origin) && java.util.Objects.equals(this.template, that.template) && java.util.Objects.equals(this.rotated, that.rotated) && this.score == that.score;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.origin, this.template, this.rotated, this.score); }

        @Override
        public String toString() {
            return "RecognizedCandidate[" + "origin=" + this.origin + ", " + "template=" + this.template + ", " + "rotated=" + this.rotated + ", " + "score=" + this.score + "]";
        }

    }

    private static class GrowthSite {
        private final BlockPos origin;
        private final Rotation rotation;

        public GrowthSite(BlockPos origin, Rotation rotation) {
            this.origin = origin;
            this.rotation = rotation;
        }

        public BlockPos origin() { return this.origin; }

        public Rotation rotation() { return this.rotation; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof GrowthSite)) return false;
            GrowthSite that = (GrowthSite) other;
            return java.util.Objects.equals(this.origin, that.origin) && java.util.Objects.equals(this.rotation, that.rotation);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.origin, this.rotation); }

        @Override
        public String toString() {
            return "GrowthSite[" + "origin=" + this.origin + ", " + "rotation=" + this.rotation + "]";
        }

    }

}

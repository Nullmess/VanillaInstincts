package fr.vanillainstincts.village;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/** Per-level transient state owned by village evolution. */
final class VillageEvolutionRuntime {
    VillageBuildingProject project;
    final Queue<VillageRepairTask> repairs = new ArrayDeque<>();
    final Map<Long, VillageRepairSnapshot> snapshots = new HashMap<>();
    long snapshotDay = Long.MIN_VALUE;
    long repairPreparedDay = Long.MIN_VALUE;
    boolean restoreAttempted;
}

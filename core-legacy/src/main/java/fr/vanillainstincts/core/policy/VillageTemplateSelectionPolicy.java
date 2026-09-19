package fr.vanillainstincts.core.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** Rotation des modèles. */
public final class VillageTemplateSelectionPolicy {
    private VillageTemplateSelectionPolicy() {
    }

    public static List<Integer> order(int[] uses, boolean[] housing,
                                      boolean housingNeeded, long selector,
                                      int limit) {
        if (uses == null || housing == null || uses.length != housing.length
                || limit <= 0) return Collections.emptyList();

        int targetUse = Integer.MAX_VALUE;
        for (int index = 0; index < uses.length; index++) {
            if (housingNeeded && !housing[index]) continue;
            targetUse = Math.min(targetUse, Math.max(0, uses[index]));
        }
        if (targetUse == Integer.MAX_VALUE) return Collections.emptyList();

        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < uses.length; index++) {
            if (housingNeeded && !housing[index]) continue;
            if (Math.max(0, uses[index]) == targetUse) candidates.add(index);
        }
        if (candidates.isEmpty()) return Collections.emptyList();

        Collections.sort(candidates);
        int shift = Math.floorMod((int) (selector ^ (selector >>> 32)),
                candidates.size());
        List<Integer> ordered = new ArrayList<>(candidates.size());
        for (int offset = 0; offset < candidates.size(); offset++) {
            ordered.add(candidates.get((shift + offset) % candidates.size()));
        }
        if (ordered.size() <= limit) return Collections.unmodifiableList(new ArrayList<Integer>(ordered));
        return Collections.unmodifiableList(new ArrayList<Integer>(ordered.subList(0, limit)));
    }
}

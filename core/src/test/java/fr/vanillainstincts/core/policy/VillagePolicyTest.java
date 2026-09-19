package fr.vanillainstincts.core.policy;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VillagePolicyTest {
    @Test
    void housingKeepsTwoReserveBeds() {
        assertEquals(8, VillageGrowthPolicy.targetBedCount(6));
        assertTrue(VillageGrowthPolicy.needsHousing(6, 7));
        assertFalse(VillageGrowthPolicy.needsHousing(6, 8));
    }

    @Test
    void golemPolicyRemainsPopulationBound() {
        assertEquals(0, VillageGolemPolicy.desiredCount(4));
        assertEquals(1, VillageGolemPolicy.desiredCount(12));
        assertEquals(2, VillageGolemPolicy.desiredCount(200));
    }

    @Test
    void templateSelectionUsesLeastBuiltModels() {
        List<Integer> order = VillageTemplateSelectionPolicy.order(
                new int[] {2, 0, 0, 1},
                new boolean[] {true, true, false, true},
                true, 0L, 4);
        assertEquals(List.of(1), order);
    }
}

package fr.vanillainstincts.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.vanillainstincts.core.model.EndermanCargoRole;
import fr.vanillainstincts.core.model.IntBounds3D;
import fr.vanillainstincts.core.model.NetherReinforcementKind;
import fr.vanillainstincts.core.model.NetherReinforcementRole;
import org.junit.jupiter.api.Test;

final class ArchitecturePolicyTest {
    @Test
    void reinforcementRulesAreMinecraftIndependent() {
        assertTrue(NetherReinforcementPolicy.shouldUseVirtualExpedition(false));
        assertEquals(1,
                NetherReinforcementPolicy.simulatedReinforcementCount(
                        NetherReinforcementKind.ZOGLIN, 0L));
        assertTrue(NetherReinforcementPolicy.returnsToNetherAfterDefeat(
                NetherReinforcementRole.REINFORCEMENT));
    }

    @Test
    void constructionSafetyMathIsPure() {
        assertTrue(GolemConstructionPolicy.dropHeightIsLethal(24.0D, 5.0D));
        assertFalse(GolemConstructionPolicy.dropHeightIsLethal(10.0D, 5.0D));
    }

    @Test
    void boundsSeparateVerticalAndHorizontalOverlap() {
        IntBounds3D ground = new IntBounds3D(0, 0, 0, 4, 4, 4);
        IntBounds3D roof = new IntBounds3D(2, 10, 2, 6, 14, 6);
        assertTrue(ground.horizontalIntersects(roof));
        assertFalse(ground.intersects(roof));
    }

    @Test
    void cargoDurationIsOwnedByCorePolicy() {
        assertTrue(EndermanCargoPolicy.carryDuration(
                EndermanCargoRole.PLAYER_RELOCATION) > 1);
    }
}

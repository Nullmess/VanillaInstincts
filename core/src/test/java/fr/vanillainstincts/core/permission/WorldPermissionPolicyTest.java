package fr.vanillainstincts.core.permission;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldPermissionPolicyTest {
    private static PermissionDecision evaluate(WorldActionType action) {
        return WorldPermissionPolicy.evaluate(action, true, true, true,
                true, true, true, false, false, false, true, true);
    }

    @Test
    void ordinaryPlacementIsAllowed() {
        assertEquals(PermissionDecision.ALLOWED,
                evaluate(WorldActionType.PLACE_BLOCK));
    }

    @Test
    void masterSwitchWins() {
        assertEquals(PermissionDecision.MOD_DISABLED,
                WorldPermissionPolicy.evaluate(WorldActionType.PLACE_BLOCK,
                        false, true, true, true, true, true, false,
                        false, false, true, true));
    }

    @Test
    void worldPermissionBlocksWorldActions() {
        assertEquals(PermissionDecision.WORLD_CHANGES_DISABLED,
                WorldPermissionPolicy.evaluate(WorldActionType.PLACE_BLOCK,
                        true, false, true, true, true, true, false,
                        false, false, true, true));
    }

    @Test
    void itemPermissionBlocksContainerActions() {
        assertEquals(PermissionDecision.ITEM_CHANGES_DISABLED,
                WorldPermissionPolicy.evaluate(
                        WorldActionType.CONTAINER_MUTATION, true, true,
                        false, true, true, true, false, false, false,
                        true, true));
    }

    @Test
    void mobGriefingAppliesToMobPlacement() {
        assertEquals(PermissionDecision.MOB_GRIEFING_DISABLED,
                WorldPermissionPolicy.evaluate(WorldActionType.PLACE_BLOCK,
                        true, true, true, false, true, true, false,
                        false, false, true, true));
    }

    @Test
    void cleanupDoesNotDependOnMobGriefing() {
        assertEquals(PermissionDecision.ALLOWED,
                WorldPermissionPolicy.evaluate(
                        WorldActionType.TEMPORARY_CLEANUP, true, true,
                        true, false, true, true, false, false, false,
                        true, true));
    }

    @Test
    void unloadedChunksAreNeverTouched() {
        assertEquals(PermissionDecision.CHUNK_UNLOADED,
                WorldPermissionPolicy.evaluate(WorldActionType.REPLACE_BLOCK,
                        true, true, true, true, false, true, false,
                        false, false, true, true));
    }

    @Test
    void worldBorderIsAuthoritative() {
        assertEquals(PermissionDecision.OUTSIDE_WORLD_BORDER,
                WorldPermissionPolicy.evaluate(WorldActionType.PLACE_BLOCK,
                        true, true, true, true, true, false, false,
                        false, false, true, true));
    }

    @Test
    void spawnProtectionIsAuthoritative() {
        assertEquals(PermissionDecision.SPAWN_PROTECTED,
                WorldPermissionPolicy.evaluate(WorldActionType.PLACE_BLOCK,
                        true, true, true, true, true, true, true,
                        false, false, true, true));
    }

    @Test
    void destructiveActionsProtectBlockEntities() {
        assertEquals(PermissionDecision.BLOCK_ENTITY_PROTECTED,
                WorldPermissionPolicy.evaluate(WorldActionType.BREAK_BLOCK,
                        true, true, true, true, true, true, false,
                        true, false, true, true));
    }

    @Test
    void replacementCannotBypassProtectedTag() {
        assertEquals(PermissionDecision.PROTECTED_BLOCK,
                WorldPermissionPolicy.evaluate(WorldActionType.REPLACE_BLOCK,
                        true, true, true, true, true, true, false,
                        false, true, true, true));
    }

    @Test
    void denialCooldownStopsRepeatedChecks() {
        assertEquals(PermissionDecision.RETRY_COOLDOWN,
                WorldPermissionPolicy.evaluate(WorldActionType.PLACE_BLOCK,
                        true, true, true, true, true, true, false,
                        false, false, false, true));
    }

    @Test
    void externalEventCanDeny() {
        assertEquals(PermissionDecision.EVENT_DENIED,
                WorldPermissionPolicy.evaluate(WorldActionType.PLACE_BLOCK,
                        true, true, true, true, true, true, false,
                        false, false, true, false));
    }

    @Test
    void atomicSetRequiresEveryDecision() {
        assertTrue(WorldPermissionPolicy.allAllowed(List.of(
                PermissionDecision.ALLOWED, PermissionDecision.ALLOWED)));
        assertFalse(WorldPermissionPolicy.allAllowed(List.of(
                PermissionDecision.ALLOWED,
                PermissionDecision.EVENT_DENIED)));
    }

    @Test
    void emptyAtomicSetIsRejected() {
        assertFalse(WorldPermissionPolicy.allAllowed(List.of()));
    }
    @Test
    void unloadedContainerIsRejected() {
        assertEquals(PermissionDecision.CHUNK_UNLOADED,
                WorldPermissionPolicy.evaluate(
                        WorldActionType.CONTAINER_MUTATION, true, true,
                        true, true, false, true, false, false, false,
                        true, true));
    }

    @Test
    void logicalItemTransferDoesNotRequireAChunk() {
        assertEquals(PermissionDecision.ALLOWED,
                WorldPermissionPolicy.evaluate(WorldActionType.ITEM_TRANSFER,
                        true, true, true, true, false, true, false,
                        false, false, true, true));
    }

}

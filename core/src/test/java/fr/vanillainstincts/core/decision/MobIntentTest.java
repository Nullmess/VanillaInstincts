package fr.vanillainstincts.core.decision;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class MobIntentTest {
    @Test
    void emergencyOwnersOverrideGenericStateMapping() {
        assertEquals(MobIntent.ENDERMAN_CARGO,
                MobIntent.from(ActionOwner.ENDERMAN_TACTICS,
                        VanillaInstinctsState.IDLE));
        assertEquals(MobIntent.VILLAGE_EVACUATION,
                MobIntent.from(ActionOwner.VILLAGER_SAFETY,
                        VanillaInstinctsState.RETURN_HOME));
    }

    @Test
    void routineStatesRemainRoutine() {
        assertEquals(MobIntent.VILLAGE_ROUTINE,
                MobIntent.fromState(VanillaInstinctsState.FISHER_WORK));
        assertEquals(MobIntent.VILLAGE_ROUTINE,
                MobIntent.fromState(VanillaInstinctsState.CLERIC_NETHER));
        assertEquals(MobIntent.VILLAGE_ROUTINE,
                MobIntent.fromState(
                        VanillaInstinctsState.GRAND_MASTER_MENTOR));
    }

    @Test
    void fixed46StatesHaveStableIntents() {
        assertEquals(MobIntent.PURSUE_TARGET,
                MobIntent.fromState(VanillaInstinctsState.HORDE_PRESSURE));
        assertEquals(MobIntent.ANIMAL_COMFORT,
                MobIntent.fromState(VanillaInstinctsState.COMFORT));
        assertEquals(MobIntent.ANIMAL_COMFORT,
                MobIntent.fromState(VanillaInstinctsState.REST));
    }

    @Test
    void everyStateMapsToAnIntent() {
        for (VanillaInstinctsState state : VanillaInstinctsState.values()) {
            assertNotNull(MobIntent.fromState(state), state.name());
        }
    }

}

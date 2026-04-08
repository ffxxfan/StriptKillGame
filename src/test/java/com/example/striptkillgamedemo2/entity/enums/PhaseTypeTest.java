package com.example.striptkillgamedemo2.entity.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseTypeTest {

    @Test
    void shouldHaveFivePhaseTypes() {
        assertEquals(5, PhaseType.values().length);
    }

    @Test
    void voteIsDefaultRequired() {
        assertTrue(PhaseType.VOTE.isDefaultRequired());
    }

    @Test
    void nonVotePhasesAreNotDefaultRequired() {
        assertFalse(PhaseType.SCRIPT_READING.isDefaultRequired());
        assertFalse(PhaseType.TURN_BASED.isDefaultRequired());
        assertFalse(PhaseType.FREE_CHAT.isDefaultRequired());
        assertFalse(PhaseType.INVESTIGATION.isDefaultRequired());
    }
}

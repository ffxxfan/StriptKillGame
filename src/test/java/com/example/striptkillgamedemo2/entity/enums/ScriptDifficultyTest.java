package com.example.striptkillgamedemo2.entity.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptDifficultyTest {

    @Test
    void enumShouldHaveFourDifficultyLevels() {
        assertEquals(4, ScriptDifficulty.values().length);
        assertEquals(1, ScriptDifficulty.EASY.getLevel());
        assertEquals(2, ScriptDifficulty.NORMAL.getLevel());
        assertEquals(3, ScriptDifficulty.HARD.getLevel());
        assertEquals(4, ScriptDifficulty.EXPERT.getLevel());
    }
}
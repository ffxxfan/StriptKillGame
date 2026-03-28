package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.example.striptkillgamedemo2.entity.enums.ScriptDifficulty;

import java.util.List;

class ScriptTest {

    @Test
    void scriptEntityShouldHaveRequiredFields() {
        Script script = Script.builder()
                .title("Mystery at the Mansion")
                .description("A classic whodunit")
                .difficulty(ScriptDifficulty.NORMAL)
                .playerCount(6)
                .coverImage("http://example.com/cover.png")
                .dmConfig("{\"mode\":\"standard\"}")
                .stages(List.of())
                .version(1)
                .build();

        assertEquals("Mystery at the Mansion", script.getTitle());
        assertEquals("A classic whodunit", script.getDescription());
        assertEquals(ScriptDifficulty.NORMAL, script.getDifficulty());
        assertEquals(6, script.getPlayerCount());
        assertEquals(1, script.getVersion());
    }
}
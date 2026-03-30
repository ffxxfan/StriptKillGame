package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ScriptStageTest {

    @Test
    void shouldBuildStageWithPhases() {
        StagePhase turnPhase = StagePhase.builder()
                .phaseId("phase-1")
                .type(PhaseType.TURN_BASED)
                .speakOrder(List.of("role1", "role2", "role3"))
                .timeLimitSeconds(60)
                .dmInstruction("让每位角色依次进行自我介绍")
                .build();

        ScriptStage stage = ScriptStage.builder()
                .stageNumber(0)
                .stageTitle("第一幕：相遇")
                .phases(List.of(turnPhase))
                .build();

        assertNotNull(stage.getPhases());
        assertEquals(1, stage.getPhases().size());
        assertEquals(PhaseType.TURN_BASED, stage.getPhases().get(0).getType());
        assertEquals(3, stage.getPhases().get(0).getSpeakOrder().size());
    }

    @Test
    void scriptStageShouldHaveRequiredFields() {
        ScriptStage stage = ScriptStage.builder()
                .stageNumber(1)
                .stageTitle("Introduction")
                .audioUrl("http://example.com/intro.mp3")
                .build();

        assertEquals(1, stage.getStageNumber());
        assertEquals("Introduction", stage.getStageTitle());
        assertNotNull(stage.getContentMap());
        assertEquals("http://example.com/intro.mp3", stage.getAudioUrl());
    }
}
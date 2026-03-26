package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ScriptStageTest {

    @Test
    void scriptStageShouldHaveRequiredFields() {
        ScriptStage stage = ScriptStage.builder()
                .stageNumber(1)
                .stageTitle("Introduction")
                .contentMap(Map.of(new ObjectId(), "Welcome to the mystery"))
                .audioUrl("http://example.com/intro.mp3")
                .build();

        assertEquals(1, stage.getStageNumber());
        assertEquals("Introduction", stage.getStageTitle());
        assertNotNull(stage.getContentMap());
        assertEquals("http://example.com/intro.mp3", stage.getAudioUrl());
    }
}
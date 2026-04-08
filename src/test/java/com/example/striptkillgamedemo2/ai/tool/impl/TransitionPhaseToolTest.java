package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.memory.MemoryManager;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.mongo.StagePhase;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.PhaseRuleEnforcer;
import com.example.striptkillgamedemo2.service.PhaseTimerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransitionPhaseToolTest {

    private TransitionPhaseTool tool;
    private LiveGameRoomService liveGameRoomService;
    private MemoryManager memoryManager;
    private SimpMessagingTemplate messagingTemplate;
    private PhaseTimerService phaseTimerService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        liveGameRoomService = mock(LiveGameRoomService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        phaseTimerService = mock(PhaseTimerService.class);
        memoryManager = mock(MemoryManager.class);
        objectMapper = new ObjectMapper();
        tool = new TransitionPhaseTool(liveGameRoomService, messagingTemplate,
                phaseTimerService, new PhaseRuleEnforcer(), memoryManager, objectMapper);
    }

    @Test
    void nextStage_blockedWhenRequiredPhaseNotCompleted() {
        LiveGameRoom room = LiveGameRoom.builder()
                .roomId("aabbccddeeff001122334455")
                .currentStage(0)
                .currentPhaseIndex(1)
                .build();

        ScriptStage stage = new ScriptStage();
        stage.setPhases(List.of(
                StagePhase.builder().type(PhaseType.FREE_CHAT).build(),
                StagePhase.builder().type(PhaseType.INVESTIGATION).build(),
                StagePhase.builder().type(PhaseType.VOTE).build()
        ));

        Script script = new Script();
        script.setStages(List.of(stage, new ScriptStage()));

        DmToolContext ctx = DmToolContext.builder()
                .room(room).script(script)
                .currentPhaseType(PhaseType.INVESTIGATION)
                .build();

        TransitionPhaseTool.Input input = new TransitionPhaseTool.Input();
        input.setAction("NEXT_STAGE");
        input.setReason("test");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(input, ctx);

        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("VOTE"));
    }

    @Test
    void nextStage_allowedWhenNoRequiredPhasesRemain() {
        LiveGameRoom room = LiveGameRoom.builder()
                .roomId("aabbccddeeff001122334455")
                .currentStage(0)
                .currentPhaseIndex(1)
                .build();

        ScriptStage stage0 = new ScriptStage();
        stage0.setPhases(List.of(
                StagePhase.builder().type(PhaseType.FREE_CHAT).build(),
                StagePhase.builder().type(PhaseType.INVESTIGATION).build()
        ));
        ScriptStage stage1 = new ScriptStage();
        stage1.setStageTitle("第二幕");
        stage1.setPhases(List.of(
                StagePhase.builder().type(PhaseType.FREE_CHAT).timeLimitSeconds(0).build()
        ));

        Script script = new Script();
        script.setStages(List.of(stage0, stage1));

        DmToolContext ctx = DmToolContext.builder()
                .room(room).script(script)
                .currentPhaseType(PhaseType.INVESTIGATION)
                .build();

        when(memoryManager.hasSummary("aabbccddeeff001122334455", 0)).thenReturn(true);
        when(liveGameRoomService.getMessages(any())).thenReturn(List.of());

        TransitionPhaseTool.Input input = new TransitionPhaseTool.Input();
        input.setAction("NEXT_STAGE");
        input.setReason("test");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(input, ctx);

        assertTrue((Boolean) result.get("success"));
        assertEquals("STAGE_ADVANCE", result.get("event"));
    }

    @Test
    void nextStage_triggersFallbackCompressionWhenNoSummary() {
        LiveGameRoom room = LiveGameRoom.builder()
                .roomId("aabbccddeeff001122334455")
                .currentStage(0)
                .currentPhaseIndex(1)
                .build();

        ScriptStage stage0 = new ScriptStage();
        stage0.setPhases(List.of(
                StagePhase.builder().type(PhaseType.FREE_CHAT).build(),
                StagePhase.builder().type(PhaseType.INVESTIGATION).build()
        ));
        ScriptStage stage1 = new ScriptStage();
        stage1.setStageTitle("第二幕");
        stage1.setPhases(List.of(
                StagePhase.builder().type(PhaseType.FREE_CHAT).timeLimitSeconds(0).build()
        ));

        Script script = new Script();
        script.setStages(List.of(stage0, stage1));

        DmToolContext ctx = DmToolContext.builder()
                .room(room).script(script)
                .currentPhaseType(PhaseType.INVESTIGATION)
                .build();

        when(memoryManager.hasSummary("aabbccddeeff001122334455", 0)).thenReturn(false);
        when(liveGameRoomService.getMessages(any())).thenReturn(List.of());

        TransitionPhaseTool.Input input = new TransitionPhaseTool.Input();
        input.setAction("NEXT_STAGE");
        input.setReason("test");

        tool.execute(input, ctx);

        verify(memoryManager).compressStage(eq("aabbccddeeff001122334455"), eq(0), anyList());
    }
}

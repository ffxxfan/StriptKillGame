package com.example.striptkillgamedemo2.ai.orchestrator;

import com.example.striptkillgamedemo2.ai.executor.AgentExecutor;
import com.example.striptkillgamedemo2.ai.executor.DmExecutor;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.ScriptCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class AgentOrchestratorTest {

    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new AgentOrchestrator(
                Mockito.mock(AgentExecutor.class),
                Mockito.mock(DmExecutor.class),
                Mockito.mock(LiveGameRoomService.class),
                Mockito.mock(ScriptCacheService.class)
        );
    }

    @Test
    void isDmRequest_detectsAtDM() {
        assertTrue(orchestrator.isDmRequest("@DM 我想搜证"));
        assertTrue(orchestrator.isDmRequest("@主持人 可以投票吗"));
    }

    @Test
    void isDmRequest_detectsSearchKeywords() {
        assertTrue(orchestrator.isDmRequest("我要去花园搜证"));
        assertTrue(orchestrator.isDmRequest("我想调查书房"));
    }

    @Test
    void isDmRequest_detectsVoteKeywords() {
        assertTrue(orchestrator.isDmRequest("我们投票吧"));
        assertTrue(orchestrator.isDmRequest("请求表决"));
    }

    @Test
    void isDmRequest_normalMessage() {
        assertFalse(orchestrator.isDmRequest("大家好，我是林默"));
        assertFalse(orchestrator.isDmRequest("我觉得苏婉很可疑"));
    }

    @Test
    void isDmRequest_freeChatRequest() {
        assertTrue(orchestrator.isDmRequest("我想自由讨论"));
    }
}

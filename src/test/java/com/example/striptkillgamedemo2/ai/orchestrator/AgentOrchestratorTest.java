package com.example.striptkillgamedemo2.ai.orchestrator;

import com.example.striptkillgamedemo2.ai.executor.AgentExecutor;
import com.example.striptkillgamedemo2.ai.executor.DmExecutor;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.ScriptCacheService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

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

    @Test
    void findNamedAiRoles_matchesRoleNameInText() {
        Role role1 = new Role();
        role1.setId(new ObjectId());
        role1.setName("苏婉");

        Role role2 = new Role();
        role2.setId(new ObjectId());
        role2.setName("林默");

        Script script = new Script();
        script.setRoles(List.of(role1, role2));

        Member m1 = Member.builder().isAi(true).roleId(role1.getId()).build();
        Member m2 = Member.builder().isAi(true).roleId(role2.getId()).build();
        LiveGameRoom room = LiveGameRoom.builder()
                .roomId("aabbccddeeff001122334455")
                .members(List.of(m1, m2))
                .build();

        List<ObjectId> result = orchestrator.findNamedAiRoles("我觉得苏婉很可疑", script, room);
        assertEquals(1, result.size());
        assertEquals(role1.getId(), result.get(0));
    }

    @Test
    void findNamedAiRoles_returnsEmptyWhenNoMatch() {
        Role role1 = new Role();
        role1.setId(new ObjectId());
        role1.setName("苏婉");

        Script script = new Script();
        script.setRoles(List.of(role1));

        Member m1 = Member.builder().isAi(true).roleId(role1.getId()).build();
        LiveGameRoom room = LiveGameRoom.builder()
                .roomId("aabbccddeeff001122334455")
                .members(List.of(m1))
                .build();

        List<ObjectId> result = orchestrator.findNamedAiRoles("今天天气不错", script, room);
        assertTrue(result.isEmpty());
    }
}

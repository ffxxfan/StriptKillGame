package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.mongo.Clue;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.redis.GameClueInstance;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizeSearchToolTest {

    @Mock
    private LiveGameRoomService liveGameRoomService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private AuthorizeSearchTool tool;

    private ObjectId roleId;
    private ObjectId clueId;
    private Role role;
    private Clue clue;
    private Script script;
    private LiveGameRoom room;
    private DmToolContext ctx;

    @BeforeEach
    void setUp() {
        roleId = new ObjectId();
        clueId = new ObjectId();

        role = Role.builder()
                .id(roleId)
                .name("Detective")
                .searchPower(2)
                .build();

        clue = Clue.builder()
                .id(clueId)
                .title("Bloody Knife")
                .content("A knife found at the crime scene")
                .locationTag(List.of("study"))
                .searchableRoleIds(List.of(roleId.toHexString()))
                .build();

        script = Script.builder()
                .id(new ObjectId())
                .title("Mystery Night")
                .roles(new ArrayList<>(List.of(role)))
                .clues(new ArrayList<>(List.of(clue)))
                .build();

        room = LiveGameRoom.builder()
                .roomId(new ObjectId().toHexString())
                .clueInstances(new ArrayList<>())
                .build();

        ctx = DmToolContext.builder()
                .room(room)
                .script(script)
                .build();
    }

    @Test
    void execute_successfulSearch_findsClueAndDeductsPower() {
        AuthorizeSearchTool.Input input = new AuthorizeSearchTool.Input();
        input.setRoomId(room.getRoomId());
        input.setRoleId(roleId.toHexString());
        input.setLocation("study");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(input, ctx);

        assertTrue((Boolean) result.get("success"));
        @SuppressWarnings("unchecked")
        List<String> foundClues = (List<String>) result.get("foundClues");
        assertEquals(1, foundClues.size());
        assertEquals("Bloody Knife", foundClues.get(0));
        assertEquals(1, result.get("remainingSearchPower"));

        // Power was deducted on the role
        assertEquals(1, role.getSearchPower());

        // Room clue instances updated
        assertEquals(1, room.getClueInstances().size());
        GameClueInstance instance = room.getClueInstances().get(0);
        assertEquals(clueId, instance.getClueId());
        assertTrue(instance.isFound());

        verify(liveGameRoomService).save(room);
        verify(messagingTemplate).convertAndSend(eq("/topic/room." + room.getRoomId()), any(java.util.Map.class));
    }

    @Test
    void execute_noSearchPower_returnsError() {
        role.setSearchPower(0);

        AuthorizeSearchTool.Input input = new AuthorizeSearchTool.Input();
        input.setRoomId(room.getRoomId());
        input.setRoleId(roleId.toHexString());
        input.setLocation("study");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(input, ctx);

        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("搜证次数已用完"));

        verifyNoInteractions(liveGameRoomService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void execute_roleNotFound_returnsError() {
        AuthorizeSearchTool.Input input = new AuthorizeSearchTool.Input();
        input.setRoomId(room.getRoomId());
        input.setRoleId(new ObjectId().toHexString()); // unknown role
        input.setLocation("study");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(input, ctx);

        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("角色不存在"));

        verifyNoInteractions(liveGameRoomService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void execute_noCluesAtLocation_returnsSuccessWithEmptyList() {
        AuthorizeSearchTool.Input input = new AuthorizeSearchTool.Input();
        input.setRoomId(room.getRoomId());
        input.setRoleId(roleId.toHexString());
        input.setLocation("kitchen"); // no clue here

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(input, ctx);

        assertTrue((Boolean) result.get("success"));
        @SuppressWarnings("unchecked")
        List<String> foundClues = (List<String>) result.get("foundClues");
        assertTrue(foundClues.isEmpty());

        // Power still deducted
        assertEquals(1, role.getSearchPower());
        verify(liveGameRoomService).save(room);
        // No CLUE_FOUND broadcast since nothing found
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void toolMetadata_isCorrect() {
        assertEquals("authorizeSearch", tool.name());
        assertFalse(tool.description().isEmpty());
        assertEquals(AuthorizeSearchTool.Input.class, tool.inputType());
    }
}

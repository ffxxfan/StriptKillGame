package com.example.striptkillgamedemo2.ai.executor;

import com.example.striptkillgamedemo2.ai.memory.MemoryManager;
import com.example.striptkillgamedemo2.ai.prompt.PromptBuilder;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.ai.tool.DmToolRegistry;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.mongo.StagePhase;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.GameChatService;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.ScriptCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class DmExecutor {

    private final ChatModel chatModel;
    private final PromptBuilder promptBuilder;
    private final MemoryManager memoryManager;
    private final DmToolRegistry dmToolRegistry;
    private final LiveGameRoomService liveGameRoomService;
    private final ScriptCacheService scriptCacheService;
    private final GameChatService gameChatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Async("aiExecutor")
    public void executeDmAction(String roomId, ObjectId triggerRoleId, String reason) {
        try {
            LiveGameRoom room = liveGameRoomService.get(roomId);
            if (room == null) return;

            Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));

            // Find DM member's roleId (isDm=true)
            ObjectId dmRoleId = room.getMembers().stream()
                    .filter(m -> m.isDm())
                    .map(m -> m.getRoleId())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            // Broadcast DM typing
            messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                    Map.of("type", "TYPING", "roleId", dmRoleId != null ? dmRoleId.toHexString() : "dm",
                            "roleName", "主持人"));

            // Build tool context
            String currentPhaseId = getCurrentPhaseId(script, room);
            DmToolContext toolCtx = DmToolContext.builder()
                    .room(room)
                    .script(script)
                    .currentPhaseId(currentPhaseId)
                    .triggerRoleId(triggerRoleId)
                    .build();

            List<ToolCallback> callbacks = dmToolRegistry.buildCallbacks(toolCtx);

            // Build DM prompt
            List<String> memoryFragments = memoryManager.getMemoryFragments(roomId, room.getCurrentStage());
            List<GameMessage> allMessages = deserializeMessages(liveGameRoomService.getMessages(new ObjectId(roomId)));
            List<GameMessage> recentMessages = memoryManager.getRecentMessages(allMessages);

            String systemPrompt = promptBuilder.buildDmPrompt(room, script, memoryFragments, recentMessages);

            // Add the reason/trigger as user message
            String userContent = reason != null ? reason : "请评估当前局势并采取适当行动。";

            // Call LLM with tools
            Prompt prompt = new Prompt(
                    List.of(
                            new SystemMessage(systemPrompt),
                            new UserMessage(userContent)
                    ),
                    ToolCallingChatOptions.builder()
                            .toolCallbacks(callbacks)
                            .build()
            );

            ChatResponse response = chatModel.call(prompt);
            String reply = response.getResult().getOutput().getText();

            // If DM has a narrative reply, send it as DM message
            if (reply != null && !reply.isBlank()) {
                if (dmRoleId != null) {
                    gameChatService.sendAiMessage(roomId, dmRoleId, reply, room);
                }
            }

            // Remove typing
            messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                    Map.of("type", "TYPING_END", "roleId", dmRoleId != null ? dmRoleId.toHexString() : "dm"));

        } catch (Exception e) {
            log.error("DM execution failed for room {}", roomId, e);
        }
    }

    private String getCurrentPhaseId(Script script, LiveGameRoom room) {
        if (script.getStages() == null || room.getCurrentStage() >= script.getStages().size()) return null;
        List<StagePhase> phases = script.getStages().get(room.getCurrentStage()).getPhases();
        if (phases == null || room.getCurrentPhaseIndex() >= phases.size()) return null;
        return phases.get(room.getCurrentPhaseIndex()).getPhaseId();
    }

    private List<GameMessage> deserializeMessages(List<String> jsonMessages) {
        return jsonMessages.stream()
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, GameMessage.class);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize message", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }
}

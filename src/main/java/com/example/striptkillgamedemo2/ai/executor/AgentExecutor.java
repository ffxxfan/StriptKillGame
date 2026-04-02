package com.example.striptkillgamedemo2.ai.executor;

import com.example.striptkillgamedemo2.ai.memory.MemoryManager;
import com.example.striptkillgamedemo2.ai.prompt.PromptBuilder;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.GameChatService;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.ScriptCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutor {

    private final ChatModel chatModel;
    private final PromptBuilder promptBuilder;
    private final MemoryManager memoryManager;
    private final LiveGameRoomService liveGameRoomService;
    private final ScriptCacheService scriptCacheService;
    private final GameChatService gameChatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Async("aiExecutor")
    public void executeAgentReply(String roomId, ObjectId roleId) {
        try {
            LiveGameRoom room = liveGameRoomService.get(roomId);
            if (room == null) return;

            Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
            Role targetRole = script.getRoles().stream()
                    .filter(r -> Objects.equals(r.getId(), roleId))
                    .findFirst()
                    .orElse(null);

            if (targetRole == null) {
                log.warn("Role {} not found in script for room {}", roleId, roomId);
                return;
            }

            String roleIdHex = roleId.toHexString();

            // Broadcast typing indicator
            messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                    Map.of("type", "TYPING", "roleId", roleIdHex,
                            "roleName", targetRole.getName()));

            // Build sandboxed prompt
            List<String> memoryFragments = memoryManager.getMemoryFragments(roomId, room.getCurrentStage());
            List<GameMessage> allMessages = deserializeMessages(liveGameRoomService.getMessages(new ObjectId(roomId)));
            List<GameMessage> recentMessages = memoryManager.getRecentMessages(allMessages);

            String promptText = promptBuilder.buildAgentPrompt(
                    room, script, targetRole,
                    room.getClueInstances(),
                    memoryFragments, recentMessages);

            // Stream LLM response and push chunks via WebSocket
            String streamId = UUID.randomUUID().toString();
            AtomicInteger seq = new AtomicInteger(0);
            StringBuilder fullReply = new StringBuilder();
            String streamTopic = "/topic/room." + roomId + ".stream";

            Flux<String> contentFlux = chatModel.stream(new Prompt(promptText))
                    .map(response -> {
                        if (response.getResult() != null
                                && response.getResult().getOutput() != null
                                && response.getResult().getOutput().getText() != null) {
                            return response.getResult().getOutput().getText();
                        }
                        return "";
                    })
                    .filter(chunk -> !chunk.isEmpty());

            contentFlux
                    .doOnNext(chunk -> {
                        fullReply.append(chunk);
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("type", "STREAM_CHUNK");
                        payload.put("streamId", streamId);
                        payload.put("roleId", roleIdHex);
                        payload.put("roleName", targetRole.getName());
                        payload.put("chunk", chunk);
                        payload.put("seq", seq.getAndIncrement());
                        messagingTemplate.convertAndSend(streamTopic, payload);
                    })
                    .doOnComplete(() -> {
                        String reply = fullReply.toString();
                        if (!reply.isBlank()) {
                            // Store complete message to Redis (no WS broadcast — chunks already sent)
                            gameChatService.storeAiMessage(roomId, roleId, reply, room);
                        }

                        // Send stream-end signal
                        Map<String, Object> endPayload = new LinkedHashMap<>();
                        endPayload.put("type", "STREAM_END");
                        endPayload.put("streamId", streamId);
                        endPayload.put("roleId", roleIdHex);
                        endPayload.put("seq", seq.getAndIncrement());
                        messagingTemplate.convertAndSend(streamTopic, endPayload);

                        // Remove typing indicator
                        messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                                Map.of("type", "TYPING_END", "roleId", roleIdHex));
                    })
                    .doOnError(error -> {
                        log.error("Agent streaming failed for role {} in room {}", roleId, roomId, error);
                        messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                                Map.of("type", "TYPING_END", "roleId", roleIdHex));
                    })
                    .blockLast(); // Block within @Async thread to keep it alive until stream completes

        } catch (Exception e) {
            log.error("Agent execution failed for role {} in room {}", roleId, roomId, e);
            messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                    Map.of("type", "TYPING_END", "roleId", roleId.toHexString()));
        }
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

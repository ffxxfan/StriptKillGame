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
import com.example.striptkillgamedemo2.service.VoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

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
    private final VoteService voteService;

    private static final String THINK_OPEN = "<think>";
    private static final String THINK_CLOSE = "</think>";
    private static final int STREAM_CHUNK_SIZE = 2;
    private static final long STREAM_CHUNK_DELAY_MS = 30;

    /** Trigger a single agent reply asynchronously. */
    @Async("aiExecutor")
    public void executeAgentReply(String roomId, ObjectId roleId) {
        doExecuteAgentReply(roomId, roleId);
    }

    /** Trigger multiple agents sequentially (one finishes before the next starts). */
    @Async("aiExecutor")
    public void executeAgentRepliesSequentially(String roomId, List<ObjectId> roleIds) {
        for (ObjectId roleId : roleIds) {
            doExecuteAgentReply(roomId, roleId);
        }
    }

    /**
     * AI agent votes using LLM reasoning.
     * Sends the vote context to the agent's prompt, parses the chosen option,
     * and calls VoteService.castVote(). Falls back to random choice on parse failure.
     */
    @Async("aiExecutor")
    public void executeAgentVote(String roomId, ObjectId roleId, String voteTitle, List<String> options) {
        try {
            LiveGameRoom room = liveGameRoomService.get(roomId);
            if (room == null || room.getActiveVote() == null) return;

            Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
            Role targetRole = script.getRoles().stream()
                    .filter(r -> Objects.equals(r.getId(), roleId))
                    .findFirst()
                    .orElse(null);
            if (targetRole == null) return;

            // Build vote prompt
            List<String> memoryFragments = memoryManager.getMemoryFragments(roomId, room.getCurrentStage());
            List<GameMessage> allMessages = deserializeMessages(liveGameRoomService.getMessages(new ObjectId(roomId)));
            List<GameMessage> recentMessages = memoryManager.getRecentMessages(allMessages);

            String agentPrompt = promptBuilder.buildAgentPrompt(
                    room, script, targetRole,
                    room.getClueInstances(),
                    memoryFragments, recentMessages);

            String voteInstruction = "\n\n## 投票任务\n" +
                    "投票主题：" + voteTitle + "\n" +
                    "选项：" + options + "\n" +
                    "根据你的角色身份和已知信息，从以上选项中选择一个。" +
                    "请只回复选项的完整文本，不要附加任何解释。";

            ChatResponse chatResponse = chatModel.call(new Prompt(agentPrompt + voteInstruction));
            String reply = "";
            if (chatResponse.getResults() != null
                    && chatResponse.getResults().size() > 1
                    && chatResponse.getResults().get(1).getOutput() != null
                    && chatResponse.getResults().get(1).getOutput().getText() != null) {
                reply = stripThinkTags(chatResponse.getResults().get(1).getOutput().getText()).trim();
            }

            // Match reply to an option (exact or contains)
            final String finalReply = reply;
            String chosen = options.stream()
                    .filter(opt -> finalReply.contains(opt) || opt.contains(finalReply))
                    .findFirst()
                    .orElse(null);

            // Fallback: random choice
            if (chosen == null) {
                chosen = options.get(new java.util.Random().nextInt(options.size()));
                log.warn("[AgentVote] could not parse AI reply '{}', falling back to random: {}", reply, chosen);
            }

            voteService.castVote(roomId, roleId, chosen);
            log.info("[AgentVote] role={}, chose='{}', room={}", targetRole.getName(), chosen, roomId);

        } catch (Exception e) {
            log.error("Agent vote failed for role {} in room {}", roleId, roomId, e);
        }
    }

    private void doExecuteAgentReply(String roomId, ObjectId roleId) {
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

            // Blocking call — avoids concurrent streaming issues and filters out thinking
            ChatResponse chatResponse = chatModel.call(new Prompt(promptText));
            String reply = "";
            if (chatResponse.getResults() != null
                    && chatResponse.getResults().get(1).getOutput() != null
                    && chatResponse.getResults().get(1).getOutput().getText() != null) {
                reply = stripThinkTags(chatResponse.getResults().get(1).getOutput().getText());
            }

            // Push final reply to frontend in small chunks for typing effect
            String streamId = UUID.randomUUID().toString();
            String streamTopic = "/topic/room." + roomId + ".stream";
            int seq = 0;

            for (int i = 0; i < reply.length(); i += STREAM_CHUNK_SIZE) {
                String chunk = reply.substring(i, Math.min(i + STREAM_CHUNK_SIZE, reply.length()));
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", "STREAM_CHUNK");
                payload.put("streamId", streamId);
                payload.put("roleId", roleIdHex);
                payload.put("roleName", targetRole.getName());
                payload.put("chunk", chunk);
                payload.put("seq", seq++);
                messagingTemplate.convertAndSend(streamTopic, payload);
                Thread.sleep(STREAM_CHUNK_DELAY_MS);
            }

            // Store message
            if (!reply.isBlank()) {
                gameChatService.storeAiMessage(roomId, roleId, reply, room);
            }

            // Send stream-end signal
            Map<String, Object> endPayload = new LinkedHashMap<>();
            endPayload.put("type", "STREAM_END");
            endPayload.put("streamId", streamId);
            endPayload.put("roleId", roleIdHex);
            endPayload.put("seq", seq);
            messagingTemplate.convertAndSend(streamTopic, endPayload);

            // Remove typing indicator
            messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                    Map.of("type", "TYPING_END", "roleId", roleIdHex));

        } catch (Exception e) {
            log.error("Agent execution failed for role {} in room {}", roleId, roomId, e);
            messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                    Map.of("type", "TYPING_END", "roleId", roleId.toHexString()));
        }
    }

    private String stripThinkTags(String text) {
        if (text == null) return "";
        return text.replaceAll("(?s)" + THINK_OPEN + ".*?" + THINK_CLOSE, "").trim();
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

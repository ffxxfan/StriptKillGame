package com.example.striptkillgamedemo2.ai.executor;

import com.example.striptkillgamedemo2.ai.memory.MemoryManager;
import com.example.striptkillgamedemo2.ai.prompt.PromptBuilder;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.ai.tool.DmToolRegistry;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.mongo.Role;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * DM（主持人）执行器。
 *
 * <p>负责调用 LLM 生成 DM 的回复和工具调用，核心功能包括：</p>
 * <ul>
 *   <li>构建 DM 系统提示词并绑定可用工具回调</li>
 *   <li>通过 Spring AI 的工具调用循环执行 DM 决策（阶段推进、投票发起等）</li>
 *   <li>将 DM 回复分块流式传输给前端</li>
 *   <li>处理代理委托（{@code agentDelegated}）— 当工具委托 AI 代理发言时抑制 DM 文本</li>
 *   <li>处理延迟轮流发言 — 在 DM 文本传输完成后再执行 AI 代理的轮流发言</li>
 * </ul>
 *
 * @see com.example.striptkillgamedemo2.ai.tool.DmToolRegistry
 * @see com.example.striptkillgamedemo2.ai.tool.DmToolContext
 */
public class DmExecutor {

    private final ChatModel chatModel;
    private final PromptBuilder promptBuilder;
    private final MemoryManager memoryManager;
    private final DmToolRegistry dmToolRegistry;
    private final AgentExecutor agentExecutor;
    private final LiveGameRoomService liveGameRoomService;
    private final ScriptCacheService scriptCacheService;
    private final GameChatService gameChatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    private static final String THINK_OPEN = "<think>";
    private static final String THINK_CLOSE = "</think>";
    /** 流式传输每个分块的字符数 */
    private static final int STREAM_CHUNK_SIZE = 2;
    /** 分块之间的延迟（毫秒） */
    private static final long STREAM_CHUNK_DELAY_MS = 30;

    /**
     * 异步执行 DM 动作。
     *
     * <p>完整流程：构建工具上下文 → 构建提示词 → 调用 LLM（含工具循环） →
     * 处理代理委托 → 流式传输回复 → 存储消息 → 执行延迟轮流发言。</p>
     *
     * @param triggerRoleId 触发此动作的角色 ID，可为 {@code null}
     * @param roomId        房间 ID
     * @param reason        触发原因或用户消息内容
     */
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

            String dmRoleIdHex = dmRoleId != null ? dmRoleId.toHexString() : "dm";

            // Broadcast DM typing
            messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                    Map.of("type", "TYPING", "roleId", dmRoleIdHex,
                            "roleName", "主持人"));

            // Build tool context
            String currentPhaseId = getCurrentPhaseId(script, room);
            DmToolContext toolCtx = DmToolContext.builder()
                    .room(room)
                    .script(script)
                    .currentPhaseId(currentPhaseId)
                    .currentPhaseType(getCurrentPhaseType(script, room))
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

            // Call LLM with tools (synchronous — tools have side effects)
            Prompt prompt = new Prompt(
                    List.of(
                            new SystemMessage(systemPrompt),
                            new UserMessage(userContent)
                    ),
                    ToolCallingChatOptions.builder()
                            .toolCallbacks(callbacks)
                            .build()
            );

            // Blocking call: Spring AI handles the full tool-calling loop internally,
            // so only the final response text is returned — no intermediate reasoning leaks.
            ChatResponse chatResponse = chatModel.call(prompt);
            String reply = extractReply(chatResponse);

            // If agents were delegated to speak (e.g. via selectRespondents),
            // suppress DM's own text — only the agents should appear in chat.
            if (toolCtx.isAgentDelegated()) {
                log.info("[DmExecutor] agent delegated, suppressing DM text for room={}", roomId);
                messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                        Map.of("type", "TYPING_END", "roleId", dmRoleIdHex));
                return;
            }

            // Push the final reply to frontend in small chunks to preserve streaming UX
            String streamId = UUID.randomUUID().toString();
            String streamTopic = "/topic/room." + roomId + ".stream";
            int seq = 0;

            for (int i = 0; i < reply.length(); i += STREAM_CHUNK_SIZE) {
                String chunk = reply.substring(i, Math.min(i + STREAM_CHUNK_SIZE, reply.length()));
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", "STREAM_CHUNK");
                payload.put("streamId", streamId);
                payload.put("roleId", dmRoleIdHex);
                payload.put("roleName", "主持人");
                payload.put("chunk", chunk);
                payload.put("seq", seq++);
                messagingTemplate.convertAndSend(streamTopic, payload);
                Thread.sleep(STREAM_CHUNK_DELAY_MS);
            }

            // Store message
            if (!reply.isBlank() && dmRoleId != null) {
                gameChatService.storeAiMessage(roomId, dmRoleId, reply, room);
            }

            // Send stream-end signal
            Map<String, Object> endPayload = new LinkedHashMap<>();
            endPayload.put("type", "STREAM_END");
            endPayload.put("streamId", streamId);
            endPayload.put("roleId", dmRoleIdHex);
            endPayload.put("seq", seq);
            messagingTemplate.convertAndSend(streamTopic, endPayload);

            // Remove typing
            messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                    Map.of("type", "TYPING_END", "roleId", dmRoleIdHex));

            // Execute deferred round-robin speech AFTER DM text is fully streamed
            if (toolCtx.getPendingRoundRobinInstruction() != null
                    && toolCtx.getPendingRoundRobinRoleIds() != null
                    && !toolCtx.getPendingRoundRobinRoleIds().isEmpty()) {
                String instruction = toolCtx.getPendingRoundRobinInstruction();
                List<ObjectId> pendingRoleIds = toolCtx.getPendingRoundRobinRoleIds();
                log.info("[DmExecutor] executing deferred round-robin: {} agents, instruction='{}'",
                        pendingRoleIds.size(), instruction);

                for (ObjectId rid : pendingRoleIds) {
                    agentExecutor.executeAgentReplySync(roomId, rid, instruction);
                }

                log.info("[DmExecutor] deferred round-robin completed for room={}", roomId);

                // Check which human players have already spoken (self-introduced)
                String callbackMsg = buildRoundRobinCallback(roomId, room, script, instruction);
                executeDmAction(roomId, null, callbackMsg);
            }

        } catch (Exception e) {
            log.error("DM execution failed for room {}", roomId, e);
        }
    }

    private String getCurrentPhaseId(Script script, LiveGameRoom room) {
        StagePhase phase = getCurrentStagePhase(script, room);
        return phase != null ? phase.getPhaseId() : null;
    }

    private PhaseType getCurrentPhaseType(Script script, LiveGameRoom room) {
        StagePhase phase = getCurrentStagePhase(script, room);
        return phase != null ? phase.getType() : null;
    }

    private StagePhase getCurrentStagePhase(Script script, LiveGameRoom room) {
        if (script.getStages() == null || room.getCurrentStage() >= script.getStages().size()) return null;
        List<StagePhase> phases = script.getStages().get(room.getCurrentStage()).getPhases();
        if (phases == null || room.getCurrentPhaseIndex() >= phases.size()) return null;
        return phases.get(room.getCurrentPhaseIndex());
    }

    private String extractReply(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResults() == null
                || chatResponse.getResults().isEmpty()) {
            return "";
        }
        var results = chatResponse.getResults();
        var generation = results.size() > 1 ? results.get(1) : results.get(0);
        if (generation.getOutput() == null || generation.getOutput().getText() == null) {
            return "";
        }
        return stripThinkTags(generation.getOutput().getText());
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

    /**
     * AI 轮流发言完成后，检查真人玩家是否已发言。
     *
     * <p>返回一条 DM 回调消息：如果所有玩家都已发言则指示 DM 推进流程，
     * 否则列出尚未发言的玩家名单。</p>
     *
     * @param roomId      房间 ID
     * @param room        游戏房间运行时状态
     * @param script      剧本数据
     * @param instruction 原始轮流发言指令
     * @return DM 回调消息
     */
    private String buildRoundRobinCallback(String roomId, LiveGameRoom room, Script script, String instruction) {
        // Identify human player roleIds
        Set<String> humanRoleIds = new HashSet<>();
        for (Member m : room.getMembers()) {
            if (!m.isAi() && !m.isDm() && m.getRoleId() != null) {
                humanRoleIds.add(m.getRoleId().toHexString());
            }
        }

        if (humanRoleIds.isEmpty()) {
            // All-AI game, proceed directly
            return "所有AI角色已完成「" + instruction + "」，本局没有真人玩家。" +
                    "请使用 transitionPhase（NEXT_PHASE）进入下一环节。";
        }

        // Check chat history for human player messages
        List<GameMessage> allMessages = deserializeMessages(
                liveGameRoomService.getMessages(new ObjectId(roomId)));
        Set<String> spokenHumanRoleIds = new HashSet<>();
        for (GameMessage msg : allMessages) {
            if (!msg.isAi() && msg.getSenderRoleId() != null
                    && humanRoleIds.contains(msg.getSenderRoleId().toHexString())) {
                spokenHumanRoleIds.add(msg.getSenderRoleId().toHexString());
            }
        }

        Set<String> notSpoken = new HashSet<>(humanRoleIds);
        notSpoken.removeAll(spokenHumanRoleIds);

        if (notSpoken.isEmpty()) {
            // All human players have already spoken
            room.getAwaitingIntroRoleIds().clear();
            liveGameRoomService.save(room);
            return "所有AI角色已完成「" + instruction + "」，所有真人玩家也已经发言完毕。" +
                    "请使用 transitionPhase（NEXT_PHASE）进入下一环节。";
        }

        // Track who still needs to introduce — AgentOrchestrator will notify DM when all done
        room.setAwaitingIntroRoleIds(notSpoken);
        liveGameRoomService.save(room);

        // Find names of players who haven't spoken
        List<String> names = new ArrayList<>();
        for (Role role : script.getRoles()) {
            if (role.getId() != null && notSpoken.contains(role.getId().toHexString())) {
                names.add(role.getName());
            }
        }

        return "所有AI角色已完成「" + instruction + "」。" +
                "以下真人玩家尚未自我介绍：" + String.join("、", names) + "。" +
                "请提醒他们进行自我介绍。当所有玩家发言完毕后系统会自动通知你推进流程。";
    }
}

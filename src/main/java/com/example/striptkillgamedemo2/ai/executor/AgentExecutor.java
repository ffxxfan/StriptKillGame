package com.example.striptkillgamedemo2.ai.executor;

import com.example.striptkillgamedemo2.ai.event.ChatMessageEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * AI 代理执行器。
 *
 * <p>负责调用 LLM 生成 AI 角色的回复，核心功能包括：</p>
 * <ul>
 *   <li>构建沙箱化的代理提示词并调用 LLM</li>
 *   <li>将回复通过 WebSocket 分块流式传输给前端，模拟打字效果</li>
 *   <li>支持 {@code [MSG]} 标记分割多条消息</li>
 *   <li>发布 {@link ChatMessageEvent} 以触发 AI-to-AI 对话链</li>
 *   <li>支持 AI 代理投票（{@link #executeAgentVote}）</li>
 * </ul>
 *
 * <p>对于推理模型（如 DeepSeek），{@code extractReply} 优先使用第二个结果（实际回复），
 * 跳过第一个结果（思考过程）。</p>
 *
 * @see com.example.striptkillgamedemo2.ai.orchestrator.AgentOrchestrator
 * @see com.example.striptkillgamedemo2.ai.prompt.PromptBuilder#buildAgentPrompt
 */
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
    private final ApplicationEventPublisher eventPublisher;

    private static final String THINK_OPEN = "<think>";
    private static final String THINK_CLOSE = "</think>";
    /** 流式传输每个分块的字符数 */
    private static final int STREAM_CHUNK_SIZE = 2;
    /** 分块之间的延迟（毫秒） */
    private static final long STREAM_CHUNK_DELAY_MS = 80;
    /** 多条消息之间的间隔（毫秒），模拟自然对话节奏 */
    private static final long MSG_GAP_DELAY_MS = 1800;

    /**
     * 异步触发单个 AI 代理回复。
     *
     * @param roomId 房间 ID
     * @param roleId 角色 ID
     */
    @Async("aiExecutor")
    public void executeAgentReply(String roomId, ObjectId roleId) {
        doExecuteAgentReply(roomId, roleId, false, null);
    }

    /**
     * 异步触发单个 AI 代理回复，支持最后一轮话题引导标志。
     *
     * @param roomId      房间 ID
     * @param roleId      角色 ID
     * @param lastAiRound 是否为本轮最后一次 AI 发言，若为 true 则引导话题至真实玩家
     */
    @Async("aiExecutor")
    public void executeAgentReply(String roomId, ObjectId roleId, boolean lastAiRound) {
        doExecuteAgentReply(roomId, roleId, lastAiRound, null);
    }

    /**
     * 异步顺序触发多个 AI 代理回复（前一个完成后再启动下一个）。
     *
     * @param roomId  房间 ID
     * @param roleIds 角色 ID 列表，按顺序执行
     */
    @Async("aiExecutor")
    public void executeAgentRepliesSequentially(String roomId, List<ObjectId> roleIds) {
        for (ObjectId roleId : roleIds) {
            doExecuteAgentReply(roomId, roleId, false, null);
        }
    }

    /**
     * 异步顺序触发多个 AI 代理回复，仅最后一个代理携带话题引导标志。
     *
     * @param roomId      房间 ID
     * @param roleIds     角色 ID 列表
     * @param lastAiRound 是否为本轮最后一批 AI 发言
     */
    @Async("aiExecutor")
    public void executeAgentRepliesSequentially(String roomId, List<ObjectId> roleIds, boolean lastAiRound) {
        for (int i = 0; i < roleIds.size(); i++) {
            // Only the last agent in the batch gets the redirect flag
            boolean isLast = lastAiRound && (i == roleIds.size() - 1);
            doExecuteAgentReply(roomId, roleIds.get(i), isLast, null);
        }
    }

    /**
     * 同步执行 AI 代理回复 — 在调用者线程上运行。
     *
     * <p>供 DM 工具使用，当 DM 需要等待代理完成后再继续流程时调用。</p>
     *
     * @param roomId           房间 ID
     * @param roleId           角色 ID
     * @param extraInstruction 附加指令，追加到代理提示词末尾作为【系统指令】，可为 {@code null}
     */
    public void executeAgentReplySync(String roomId, ObjectId roleId, String extraInstruction) {
        doExecuteAgentReply(roomId, roleId, false, extraInstruction);
    }

    /**
     * AI 代理通过 LLM 推理进行投票。
     *
     * <p>将投票上下文注入代理提示词，解析 LLM 返回的选项，
     * 调用 {@link VoteService#castVote} 提交投票。若解析失败则随机选择。</p>
     *
     * @param roomId    房间 ID
     * @param roleId    角色 ID
     * @param voteTitle 投票主题
     * @param options   可选项列表
     */
    @Async("aiExecutor")
    public void executeAgentVote(String roomId, ObjectId roleId, String voteTitle, List<String> options) {
        try {
            LiveGameRoom room = liveGameRoomService.get(roomId);
            if (room == null || room.getActiveVote() == null) return;

            if (room.getEliminatedRoleIds() != null && room.getEliminatedRoleIds().contains(roleId.toHexString())) {
                log.info("[AgentVote] skipping eliminated role={}, room={}", roleId, roomId);
                return;
            }

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
            String reply = extractReply(chatResponse).trim();

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

    /**
     * 执行 AI 代理回复的核心逻辑。
     *
     * <p>完整流程：检查角色状态 → 广播输入指示 → 构建提示词 → 调用 LLM →
     * 分块流式传输 → 存储消息 → 发布事件。</p>
     *
     * @param roomId           房间 ID
     * @param roleId           角色 ID
     * @param lastAiRound      是否为最后一轮 AI 发言
     * @param extraInstruction 附加系统指令，可为 {@code null}
     */
    private void doExecuteAgentReply(String roomId, ObjectId roleId, boolean lastAiRound, String extraInstruction) {
        try {
            LiveGameRoom room = liveGameRoomService.get(roomId);
            if (room == null) return;

            // Skip eliminated agents
            String roleIdHex = roleId.toHexString();
            if (room.getEliminatedRoleIds() != null && room.getEliminatedRoleIds().contains(roleIdHex)) {
                log.info("[AgentReply] skipping eliminated role={}, room={}", roleIdHex, roomId);
                return;
            }

            Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
            Role targetRole = script.getRoles().stream()
                    .filter(r -> Objects.equals(r.getId(), roleId))
                    .findFirst()
                    .orElse(null);

            if (targetRole == null) {
                log.warn("Role {} not found in script for room {}", roleId, roomId);
                return;
            }

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

            // Last AI round: inject redirect instruction with real player names
            if (lastAiRound) {
                List<String> humanNames = room.getMembers().stream()
                        .filter(m -> !m.isAi() && !m.isDm() && m.getRoleId() != null)
                        .map(m -> script.getRoles().stream()
                                .filter(r -> Objects.equals(r.getId(), m.getRoleId()))
                                .map(Role::getName)
                                .findFirst().orElse(null))
                        .filter(Objects::nonNull)
                        .toList();
                String playerList = humanNames.isEmpty() ? "在场的真实玩家" : String.join("、", humanNames);
                promptText += "\n\n【系统指令】这是你在本轮 AI 对话中的最后一次发言机会。" +
                        "真实玩家是：" + playerList + "。" +
                        "请将话题自然地引向这些真实玩家，点名邀请他们参与讨论或表达看法。" +
                        "如有流程上的需求（如请求投票、搜证等），则引向主持人（DM）。";
                log.info("[AgentReply] lastAiRound redirect injected for role={}, humanPlayers={}", roleId, humanNames);
            }

            // Extra instruction from tool (e.g. "请进行自我介绍")
            if (extraInstruction != null && !extraInstruction.isBlank()) {
                promptText += "\n\n【系统指令】" + extraInstruction;
            }

            // Blocking call — avoids concurrent streaming issues and filters out thinking
            log.info("[AgentReply] calling LLM for role={}, room={}, promptLen={}, promptContext={}",
                    targetRole.getName(), roomId, promptText.length(), promptText);
            ChatResponse chatResponse = chatModel.call(new Prompt(promptText));
            log.info("[AgentReply] LLM returned, resultsCount={}",
                    chatResponse.getResults() != null ? chatResponse.getResults().size() : 0);
            String reply = extractReply(chatResponse);
            log.info("[AgentReply] extractedReply length={}, blank={}", reply.length(), reply.isBlank());

            // Split reply by [MSG] into separate messages
            String[] segments = reply.split("\\[MSG\\]");
            String streamTopic = "/topic/room." + roomId + ".stream";

            for (int s = 0; s < segments.length; s++) {
                String segment = segments[s].trim();
                if (segment.isEmpty()) continue;
                if (isInvalidContent(segment)) {
                    continue;
                }

                // Pause between messages for natural feel
                if (s > 0) {
                    Thread.sleep(MSG_GAP_DELAY_MS);
                }

                // Each segment gets its own stream
                String streamId = UUID.randomUUID().toString();
                int seq = 0;

                for (int i = 0; i < segment.length(); i += STREAM_CHUNK_SIZE) {
                    String chunk = segment.substring(i, Math.min(i + STREAM_CHUNK_SIZE, segment.length()));
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

                // Store each segment as a separate message
                gameChatService.storeAiMessage(roomId, roleId, segment, room);

                // Send stream-end signal for this segment
                Map<String, Object> endPayload = new LinkedHashMap<>();
                endPayload.put("type", "STREAM_END");
                endPayload.put("streamId", streamId);
                endPayload.put("roleId", roleIdHex);
                endPayload.put("seq", seq);
                messagingTemplate.convertAndSend(streamTopic, endPayload);
            }

            // Remove typing indicator
            messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                    Map.of("type", "TYPING_END", "roleId", roleIdHex));

            // Publish event so orchestrator can advance turn in TURN_BASED mode
            eventPublisher.publishEvent(new ChatMessageEvent(
                    this, roomId, roleId, reply, true));

        } catch (Exception e) {
            log.error("Agent execution failed for role {} in room {}", roleId, roomId, e);
            messagingTemplate.convertAndSend("/topic/room." + roomId + ".signal",
                    Map.of("type", "TYPING_END", "roleId", roleId.toHexString()));
        }
    }

    /**
     * 过滤无效内容：空、空白、纯标点、纯符号、只有一个顿号等
     */
    private boolean isInvalidContent(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        // 正则：只匹配 标点符号、空白、特殊符号，没有任何真实文字
        String regex = "^[\\p{Punct}\\p{Space}\\p{InCJK_Symbols_and_Punctuation}]*$";
        return text.matches(regex);
    }

    /**
     * 从 ChatResponse 中提取回复文本。
     *
     * <p>优先使用第二个结果（index 1），这是推理模型（如 DeepSeek）的实际回复，
     * index 0 为思考过程。如果只有一个结果则使用 index 0 并剥离 think 标签。</p>
     *
     * @param chatResponse LLM 响应
     * @return 提取的回复文本
     */
    private String extractReply(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResults() == null
                || chatResponse.getResults().isEmpty()) {
            return "";
        }
        var results = chatResponse.getResults();
        // Try index 1 first (reasoning model reply), fall back to index 0
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
}

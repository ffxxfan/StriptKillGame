package com.example.striptkillgamedemo2.ai.event;

import lombok.Getter;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEvent;

/**
 * 聊天消息事件。
 *
 * <p>当房间内产生一条聊天消息（玩家或 AI）时发布此事件。
 * {@link com.example.striptkillgamedemo2.ai.orchestrator.AgentOrchestrator} 监听此事件，
 * 通过三层响应策略决定哪些 AI 代理需要回复。</p>
 *
 * <p>{@code fromAi} 用于区分消息来源（玩家 vs AI），{@code lastAiRound} 标记是否为本轮 AI 对话的最后一次发言，
 * 触发话题引导至真实玩家的逻辑。</p>
 *
 * @see com.example.striptkillgamedemo2.ai.orchestrator.AgentOrchestrator#onChatMessage(ChatMessageEvent)
 */
@Getter
public class ChatMessageEvent extends ApplicationEvent {
    /** 房间 ID */
    private final String roomId;
    /** 发送者角色 ID */
    private final ObjectId senderRoleId;
    /** 消息内容 */
    private final String content;
    /** 是否来自 AI 代理 */
    private final boolean fromAi;
    /** 是否为本轮 AI 对话的最后一次发言 */
    private final boolean lastAiRound;

    /**
     * 构造聊天消息事件（默认非最后一轮）。
     *
     * @param source       事件源
     * @param roomId       房间 ID
     * @param senderRoleId 发送者角色 ID
     * @param content      消息内容
     * @param fromAi       是否来自 AI
     */
    public ChatMessageEvent(Object source, String roomId, ObjectId senderRoleId,
                            String content, boolean fromAi) {
        this(source, roomId, senderRoleId, content, fromAi, false);
    }

    /**
     * 构造聊天消息事件。
     *
     * @param source       事件源
     * @param roomId       房间 ID
     * @param senderRoleId 发送者角色 ID
     * @param content      消息内容
     * @param fromAi       是否来自 AI
     * @param lastAiRound  是否为本轮 AI 最后一次发言
     */
    public ChatMessageEvent(Object source, String roomId, ObjectId senderRoleId,
                            String content, boolean fromAi, boolean lastAiRound) {
        super(source);
        this.roomId = roomId;
        this.senderRoleId = senderRoleId;
        this.content = content;
        this.fromAi = fromAi;
        this.lastAiRound = lastAiRound;
    }
}

package com.example.striptkillgamedemo2.ai.event;

import lombok.Getter;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEvent;

/**
 * AI 代理回复事件。
 *
 * <p>当 AI 代理完成一次回复后发布此事件，用于通知编排器（{@link com.example.striptkillgamedemo2.ai.orchestrator.AgentOrchestrator}）
 * 进行后续处理，如轮次推进等。</p>
 *
 * @see com.example.striptkillgamedemo2.ai.executor.AgentExecutor
 */
@Getter
public class AgentReplyEvent extends ApplicationEvent {
    /** 房间 ID */
    private final String roomId;
    /** 回复的角色 ID */
    private final ObjectId roleId;

    /**
     * 构造 AI 代理回复事件。
     *
     * @param source 事件源
     * @param roomId 房间 ID
     * @param roleId 回复的角色 ID
     */
    public AgentReplyEvent(Object source, String roomId, ObjectId roleId) {
        super(source);
        this.roomId = roomId;
        this.roleId = roleId;
    }
}

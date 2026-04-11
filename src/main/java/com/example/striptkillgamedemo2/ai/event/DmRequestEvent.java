package com.example.striptkillgamedemo2.ai.event;

import lombok.Getter;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEvent;

/**
 * DM（主持人）请求事件。
 *
 * <p>当需要触发 DM 执行动作时发布此事件，例如玩家发送了需要 DM 介入的消息。
 * 包含触发角色 ID 和触发原因，供 DM 执行器决策使用。</p>
 *
 * @see com.example.striptkillgamedemo2.ai.executor.DmExecutor
 */
@Getter
public class DmRequestEvent extends ApplicationEvent {
    /** 房间 ID */
    private final String roomId;
    /** 触发此请求的角色 ID */
    private final ObjectId triggerRoleId;
    /** 触发原因描述 */
    private final String reason;

    /**
     * 构造 DM 请求事件。
     *
     * @param source        事件源
     * @param roomId        房间 ID
     * @param triggerRoleId 触发角色 ID
     * @param reason        触发原因
     */
    public DmRequestEvent(Object source, String roomId, ObjectId triggerRoleId, String reason) {
        super(source);
        this.roomId = roomId;
        this.triggerRoleId = triggerRoleId;
        this.reason = reason;
    }
}

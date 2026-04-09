package com.example.striptkillgamedemo2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息 DTO。
 * <p>
 * 推送给前端的聊天消息结构，来源包括玩家发言、AI 代理发言和 DM 系统消息。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    /** 消息 ID，用于去重、引用。 */
    private String messageId;
    /** 发送者角色 ID。 */
    private String senderRoleId;
    /** 发送者角色名称，便于前端直接展示。 */
    private String senderRoleName;
    /** 发送者头像 URL。 */
    private String senderAvatar;
    /** 是否来自 AI 代理。 */
    @JsonProperty("isAi")
    private boolean isAi;
    /** 消息内容。 */
    private String content;
    /** 服务端消息时间戳。 */
    private LocalDateTime timestamp;
}

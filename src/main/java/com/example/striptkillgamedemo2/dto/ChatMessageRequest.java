package com.example.striptkillgamedemo2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 聊天消息发送请求 DTO。
 * <p>
 * 玩家通过 WebSocket 或 REST 通道在房间内发言时所使用的请求体。
 * </p>
 */
@Data
public class ChatMessageRequest {
    /** 发言内容，不能为空。 */
    @NotBlank(message = "消息内容不能为空")
    private String content;
}

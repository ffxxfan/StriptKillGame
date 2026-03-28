package com.example.striptkillgamedemo2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatMessageRequest {
    @NotBlank(message = "消息内容不能为空")
    private String content;
}

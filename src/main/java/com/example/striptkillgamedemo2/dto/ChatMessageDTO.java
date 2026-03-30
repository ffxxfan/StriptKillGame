package com.example.striptkillgamedemo2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private String messageId;
    private String senderRoleId;
    private String senderRoleName;
    private String senderAvatar;
    @JsonProperty("isAi")
    private boolean isAi;
    private String content;
    private LocalDateTime timestamp;
}

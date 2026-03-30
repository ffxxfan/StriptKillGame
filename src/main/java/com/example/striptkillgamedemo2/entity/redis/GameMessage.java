package com.example.striptkillgamedemo2.entity.redis;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage {
    @NotBlank
    private ObjectId messageId;
    @NotBlank
    private ObjectId gameRoomId;
    @NotBlank
    private ObjectId senderRoleId;
    @JsonProperty("isAi")
    private boolean isAi;
    private String senderRoleName;
    private String senderAvatar;
    private String content;
    private List<ObjectId> receiverRoleIds;
    private LocalDateTime timestamp;
}

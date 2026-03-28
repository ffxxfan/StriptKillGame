package com.example.striptkillgamedemo2.entity.redis;

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
    private String messageId;
    private ObjectId gameRoomId;
    private ObjectId senderRoleId;
    private boolean isAi;
    private String senderRoleName;
    private String senderAvatar;
    private String content;
    private List<ObjectId> receiverRoleIds;
    private LocalDateTime timestamp;
}

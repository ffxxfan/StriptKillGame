package com.example.striptkillgamedemo2.entity.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GameMessage representing real-time game messages during gameplay.
 * Stored in Redis List with key: game:{roomId}:messages
 *
 * Fields:
 * - messageId: Unique message identifier
 * - gameRoomId: Reference to the game room
 * - senderRoleId: Role ID of the sender
 * - senderUserId: User ID of the sender (null for NPCs)
 * - content: Message content
 * - receiverRoleIds: Roles authorized to view this message
 * - timestamp: When the message was sent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage {
    private String messageId;
    private ObjectId gameRoomId;
    private ObjectId senderRoleId;
    private ObjectId senderUserId;
    private String content;
    private List<ObjectId> receiverRoleIds;
    private LocalDateTime timestamp;
}
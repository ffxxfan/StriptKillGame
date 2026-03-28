package com.example.striptkillgamedemo2.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Member embedded entity representing a player or NPC in a game room.
 * Embedded within GameRoom.
 *
 * Fields:
 * - userId: User ID. Null for NPCs.
 * - roleId: Role ID this member is playing.
 * - isAi: Whether this member is controlled by AI.
 * - isDm: Whether this member is Dungeon Master.
 * - isOnline: Connection status for real-time features.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {
    private ObjectId userId;
    private ObjectId roleId;
    @Field("isAi")
    private boolean isAi = false;
    @Field("isDm")
    private boolean isDm = false;
    @Field("isOnline")
    private boolean isOnline = true;
    private String description; // 如果是 AI 则表示扮演的性格
}

package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GameRoom entity representing lightweight room metadata.
 * Heavy runtime state is stored in Redis.
 *
 * Fields:
 * - scriptId: Reference to -> script being played
 * - status: WAITING, PLAYING, or FINISHED
 * - currentStage: Current game stage number
 * - members: List of players and NPCs in this room
 * - startTime: When game started (for audit)
 * - endTime: When game ended (for audit)
 * - configuration: Game-specific configuration overrides
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "game_rooms")
public class GameRoom {
    @Id
    private ObjectId roomId;

    @Indexed
    private ObjectId scriptId;

    private GameRoomStatus status = GameRoomStatus.WAITING;

    private int currentStage = 0;

    private List<Member> members;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String configuration;
}

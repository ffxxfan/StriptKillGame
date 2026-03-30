package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * GameRoom entity representing a persistent game room record in MongoDB.
 * Runtime state is stored in LiveGameRoom (Redis).
 * MongoDB is updated at milestones (start / stage advance / end).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "game_rooms")
public class GameRoom {
    @Id
    private ObjectId roomId;
    private ObjectId scriptId;
    private GameRoomStatus status;
    private int currentStage;

    @Builder.Default
    private List<Member> members = new ArrayList<>();

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String configuration;
}

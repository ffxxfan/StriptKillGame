package com.example.striptkillgamedemo2.entity.mongo;

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
 * GameRecord entity representing persistent summary created after a game ends.
 * Used for history and analysis.
 *
 * Fields:
 * - roomId: Reference to game room
 * - scriptTitle: Title of script played
 * - winnerUserIds: IDs of winning users
 * - winnerRoleIds: IDs of winning roles
 * - aiSummary: AI-generated summary of game
 * - fullChatLog: Key messages from game log
 * - startTime: When game started
 * - endTime: When game ended
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "game_records")
public class GameRecord {
    @Id
    private ObjectId recordId;

    @Indexed
    private ObjectId roomId;

    private String scriptTitle;

    private List<ObjectId> winnerUserIds;

    private List<ObjectId> winnerRoleIds;

    private String aiSummary;

    private List<String> fullChatLog;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}

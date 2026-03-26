package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GameRecordTest {

    @Test
    void gameRecordEntityShouldHaveRequiredFields() {
        GameRecord record = GameRecord.builder()
                .roomId(new ObjectId())
                .scriptTitle("Mystery at the Mansion")
                .winnerUserIds(List.of(new ObjectId()))
                .winnerRoleIds(List.of(new ObjectId()))
                .aiSummary("The killer was revealed")
                .fullChatLog(List.of("Player 1: I found a clue"))
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .build();

        assertEquals("Mystery at the Mansion", record.getScriptTitle());
        assertEquals("The killer was revealed", record.getAiSummary());
        assertNotNull(record.getWinnerUserIds());
        assertNotNull(record.getWinnerRoleIds());
        assertNotNull(record.getStartTime());
        assertNotNull(record.getEndTime());
    }
}
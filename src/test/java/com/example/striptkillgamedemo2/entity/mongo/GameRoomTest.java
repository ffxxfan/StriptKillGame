package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;

class GameRoomTest {

    @Test
    void gameRoomEntityShouldHaveRequiredFields() {
        GameRoom room = GameRoom.builder()
                .roomId(new ObjectId())
                .scriptId(new ObjectId())
                .status(GameRoomStatus.WAITING)
                .currentStage(0)
                .members(List.of())
                .startTime(LocalDateTime.now())
                .configuration("{}")
                .build();

        assertNotNull(room.getRoomId());
        assertNotNull(room.getScriptId());
        assertEquals(GameRoomStatus.WAITING, room.getStatus());
        assertEquals(0, room.getCurrentStage());
        assertNotNull(room.getStartTime());
    }
}
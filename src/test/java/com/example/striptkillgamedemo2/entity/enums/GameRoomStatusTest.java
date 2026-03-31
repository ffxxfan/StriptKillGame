package com.example.striptkillgamedemo2.entity.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameRoomStatusTest {

    @Test
    void enumValuesShouldMatchDesign() {
        assertEquals(4, GameRoomStatus.values().length);
        assertNotNull(GameRoomStatus.WAITING);
        assertNotNull(GameRoomStatus.PLAYING);
        assertNotNull(GameRoomStatus.CURRENT_STAGE);
        assertNotNull(GameRoomStatus.FINISHED);
    }
}
package com.example.striptkillgamedemo2.entity.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameRoomStatusTest {

    @Test
    void enumValuesShouldMatchDesign() {
        // These are the three states defined in the spec
        assertEquals(3, GameRoomStatus.values().length);
        assertNotNull(GameRoomStatus.WAITING);
        assertNotNull(GameRoomStatus.PLAYING);
        assertNotNull(GameRoomStatus.FINISHED);
    }
}
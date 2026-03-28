package com.example.striptkillgamedemo2.entity.redis;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GameMessageTest {

    @Test
    void gameMessageShouldHaveRequiredFields() {
        GameMessage message = GameMessage.builder()
                .messageId("msg:123")
                .gameRoomId(new ObjectId())
                .senderRoleId(new ObjectId())
                .isAi(false)
                .senderRoleName("侦探")
                .senderAvatar("avatar.png")
                .content("I found a clue!")
                .receiverRoleIds(List.of(new ObjectId()))
                .timestamp(LocalDateTime.now())
                .build();

        assertEquals("msg:123", message.getMessageId());
        assertNotNull(message.getGameRoomId());
        assertNotNull(message.getSenderRoleId());
        assertEquals("I found a clue!", message.getContent());
        assertNotNull(message.getReceiverRoleIds());
        assertNotNull(message.getTimestamp());
    }
}
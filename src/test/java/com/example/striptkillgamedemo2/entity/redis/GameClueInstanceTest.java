package com.example.striptkillgamedemo2.entity.redis;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GameClueInstanceTest {

    @Test
    void gameClueInstanceShouldHaveRequiredFields() {
        ObjectId instanceId = new ObjectId();
        GameClueInstance instance = GameClueInstance.builder()
                .id(instanceId)
                .clueId(new ObjectId())
                .ownerRoleIds(List.of(new ObjectId()))
                .isPublic(false)
                .isFound(true)
                .discoveredAt(LocalDateTime.now())
                .build();

        assertEquals(instanceId, instance.getId());
        assertNotNull(instance.getClueId());
        assertFalse(instance.isPublic());
        assertTrue(instance.isFound());
        assertNotNull(instance.getDiscoveredAt());
    }
}
package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.ClueType;
import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import com.example.striptkillgamedemo2.entity.enums.ClueType;

class ClueTest {

    @Test
    void clueEntityShouldHaveRequiredFields() {
        Clue clue = Clue.builder()
                .scriptId(new ObjectId())
                .title("Bloody Knife")
                .type(ClueType.TEXT)
                .content("Found a knife with blood stains")
                .isInitialHidden(true)
                .stageNumber(2)
                .searchableRoleIds(List.of(new ObjectId()))
                .build();

        assertEquals("Bloody Knife", clue.getTitle());
        assertEquals(ClueType.TEXT, clue.getType());
        assertEquals("Found a knife with blood stains", clue.getContent());
        assertTrue(clue.isInitialHidden());
        assertEquals(2, clue.getStageNumber());
    }
}
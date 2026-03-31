package com.example.striptkillgamedemo2.entity.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClueTypeTest {

    @Test
    void enumShouldHaveTextAndImageTypes() {
        assertEquals(3, ClueType.values().length);
        assertNotNull(ClueType.TEXT);
        assertNotNull(ClueType.IMAGE);
        assertNotNull(ClueType.VIDEO);
    }
}
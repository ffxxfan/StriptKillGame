package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    @Test
    void roleEntityShouldHaveRequiredFields() {
        Role role = Role.builder()
                .id(new ObjectId())
                .name("Detective Smith")
                .avatar("http://example.com/detective.png")
                .isNpc(false)
                .prompt("You are a clever detective")
                .secret("You know the real killer")
                .locationTag("Living Room")
                .searchPower(5)
                .build();

        assertEquals("Detective Smith", role.getName());
        assertFalse(role.isNpc());
        assertEquals("Living Room", role.getLocationTag());
        assertEquals(5, role.getSearchPower());
    }
}
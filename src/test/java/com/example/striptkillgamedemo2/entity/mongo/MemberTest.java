package com.example.striptkillgamedemo2.entity.mongo;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import static org.junit.jupiter.api.Assertions.*;

class MemberTest {

    @Test
    void memberEntityShouldHaveRequiredFields() {
        Member member = Member.builder()
                .userId(new ObjectId())
                .roleId(new ObjectId())
                .isAi(false)
                .isDm(false)
                .isOnline(true)
                .build();

        assertNotNull(member.getUserId());
        assertNotNull(member.getRoleId());
        assertFalse(member.isAi());
        assertFalse(member.isDm());
        assertTrue(member.isOnline());
    }
}
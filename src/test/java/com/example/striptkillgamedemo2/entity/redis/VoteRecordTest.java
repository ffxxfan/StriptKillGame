package com.example.striptkillgamedemo2.entity.redis;

import org.junit.jupiter.api.Test;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class VoteRecordTest {

    @Test
    void voteRecordShouldHaveRequiredFields() {
        ObjectId voteId = new ObjectId();
        VoteRecord vote = VoteRecord.builder()
                .id(voteId)
                .gameRoomId(new ObjectId())
                .voterUserId(new ObjectId())
                .stageNumber(2)
                .votedRoleId(new ObjectId())
                .voteCategory("elimination")
                .voteReason("Suspicious behavior")
                .voteWeight(1)
                .timestamp(LocalDateTime.now())
                .build();

        assertEquals(voteId, vote.getId());
        assertNotNull(vote.getGameRoomId());
        assertNotNull(vote.getVoterUserId());
        assertEquals(2, vote.getStageNumber());
        assertNotNull(vote.getVotedRoleId());
        assertEquals("elimination", vote.getVoteCategory());
        assertEquals("Suspicious behavior", vote.getVoteReason());
        assertEquals(1, vote.getVoteWeight());
    }
}
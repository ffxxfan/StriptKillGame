package com.example.striptkillgamedemo2.entity.redis;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoteSessionTest {

    @Test
    void shouldBuildVoteSession() {
        VoteSession vote = VoteSession.builder()
                .voteId("vote-1")
                .title("谁是凶手？")
                .options(List.of("林默", "苏婉", "陈探长"))
                .deadline(LocalDateTime.now().plusSeconds(120))
                .build();

        assertEquals("vote-1", vote.getVoteId());
        assertEquals(3, vote.getOptions().size());
        assertNotNull(vote.getResults());
        assertTrue(vote.getResults().isEmpty());
    }

    @Test
    void shouldTrackVoteResults() {
        VoteSession vote = VoteSession.builder()
                .voteId("vote-1")
                .title("谁是凶手？")
                .options(List.of("林默", "苏婉"))
                .build();

        vote.getResults().put("role1", "林默");
        vote.getResults().put("role2", "苏婉");
        vote.getResults().put("role3", "林默");

        assertEquals(3, vote.getResults().size());
    }
}

package com.example.striptkillgamedemo2.entity.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteSession {
    private String voteId;
    private String title;
    private List<String> options;
    @Builder.Default
    private Map<String, String> results = new HashMap<>();
    private LocalDateTime deadline;
}

package com.example.striptkillgamedemo2.ai.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameReviewResult {
    private String narrative;
    private String truthReveal;
    private List<RoleScore> roleScores;
    private List<String> unresolvedMysteries;
    private Mvp mvp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleScore {
        private String roleId;
        private String roleName;
        private int score;
        private List<String> highlights;
        private List<String> missedClues;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Mvp {
        private String roleId;
        private String reason;
    }
}

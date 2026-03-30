package com.example.striptkillgamedemo2.ai.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StageSummary {
    private int stageNumber;
    private List<KeyEvent> keyEvents;
    private List<RelationshipChange> relationshipChanges;
    private List<String> unresolved;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class KeyEvent {
        private String type;
        private String from;
        private String to;
        private String summary;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RelationshipChange {
        private String from;
        private String to;
        private String change;
    }
}

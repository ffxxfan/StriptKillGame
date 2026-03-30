package com.example.striptkillgamedemo2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StageContentDTO {
    private int stageNumber;
    private String stageTitle;
    private String content;      // role-specific content (null if not in contentMap)
    private String audioUrl;
    private int totalStages;
    @JsonProperty("isLastStage")
    private boolean isLastStage;
}

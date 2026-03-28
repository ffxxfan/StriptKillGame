package com.example.striptkillgamedemo2.dto;

import com.example.striptkillgamedemo2.entity.enums.ScriptDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptSummaryDTO {
    private String id;
    private String title;
    private String description;
    private ScriptDifficulty difficulty;
    private int playerCount;
    private String coverImage;
}

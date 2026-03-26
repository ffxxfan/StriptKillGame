package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.ScriptDifficulty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Script entity representing game templates containing all static game content.
 *
 * Fields:
 * - dmConfig: Flexible JSON configuration for DM hosting style
 * - stages: Define game progression with role-specific content
 * - version: Script version for tracking updates and preventing breaking changes
 * - configuration: Game-specific configuration overrides
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "scripts")
public class Script {
    @Id
    private ObjectId
id;

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private ScriptDifficulty difficulty;

    @Min(2)
    private int playerCount;

    private String coverImage;

    private String dmConfig;

    private List<ScriptStage> stages;

    private int version = 1;

    private String configuration;
}

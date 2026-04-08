package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.ClueType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 *   Clue entity representing static clue templates.
 *   Multiple instances can be created during gameplay via GameClueInstance.
 *
 *   Fields:
 *   - type: TEXT or IMAGE clue type
 *   - content: Text content for TEXT type clues
 *   - imageUrl: Image URL for IMAGE type clues
 *   - isInitialHidden: Whether this clue is hidden at game start
 *   - stageNumber: Stage number when this clue becomes available
 *   - searchableRoleIds: IDs of roles that can search for this clue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Clue {
    private ObjectId id;
    private String title;
    private ClueType type; // TEXT, IMAGE, AUDIO
    private String content;
    private String imageUrl;

    // 控制逻辑
    @JsonProperty("isInitialHidden")
    private boolean isInitialHidden = true; // 是否初始隐藏
    private List<String> searchableRoleIds; // 哪些角色可以搜到这个线索
    private List<String> locationTag;             // 所在地点
    private List<Integer> stages; // Stages where this clue can be discovered; null/empty = any stage
    private String visibility;    // "PUBLIC" | "PRIVATE"; null defaults to PUBLIC
}

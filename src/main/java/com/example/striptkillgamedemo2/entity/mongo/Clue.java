package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.ClueType;
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
@Document(collection = "clues")
public class Clue {
    @Id
    private ObjectId id;

    @Indexed
    private ObjectId scriptId;

    @NotBlank
    private String title;

    @NotNull
    private ClueType type;

    private String content;

    private String imageUrl;

    private boolean isInitialHidden = true;

    private int stageNumber;

    private List<ObjectId> searchableRoleIds;
}

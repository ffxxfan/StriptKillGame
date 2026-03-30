package com.example.striptkillgamedemo2.entity.redis;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GameClueInstance representing dynamic state of a clue during gameplay.
 * Stored in Redis with key format: game:{roomId}:clue:{clueId}
 * Not persisted after game ends.
 *
 * Fields:
 * - id: Redis key
 * - clueId: Reference to Clue template
 * - ownerRoleIds: Roles currently holding this clue (multiple allowed)
 * - isPublic: Whether this clue is revealed to all players
 * - isFound: Whether this clue has been discovered by anyone
 * - discoveredAt: Timestamp when this clue was discovered (for audit)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameClueInstance {
    @NotBlank
    private ObjectId id;
    @NotBlank
    private ObjectId clueId;
    private List<ObjectId> ownerRoleIds;
    @JsonProperty("isPublic")
    private boolean isPublic;
    @JsonProperty("isFound")
    private boolean isFound = false;
    private LocalDateTime discoveredAt;
}

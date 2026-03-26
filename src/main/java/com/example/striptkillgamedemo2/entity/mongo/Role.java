package com.example.striptkillgamedemo2.entity.mongo;

import jakarta.validation.constraints.NotBlank;
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
 * Role entity representing character definition within a script.
 *
 * Note on Clue-Role Relationship:
 * Clue discoverability is controlled by Clue.searchableRoleIds (source of truth).
 * Role.selfClueIds is for convenience/query optimization and should always match
 * reverse lookup from Clue collection. The system validates consistency on game start.
 *
 * Fields:
 * - isNpc: Whether this role is played by an AI agent
 * - prompt: Core AI prompt instructions for NPCs
 * - secret: Secret information that must not be revealed to other players
 * - selfClueIds: IDs of clues this role can search for
 * - locationTag: Optional tag indicating search location
 * - searchPower: Optional search action points for limiting searches
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "roles")
public class Role {
    @Id
    private ObjectId id;

    @Indexed
    private ObjectId scriptId;

    @NotBlank
    private String name;

    private String avatar;

    private boolean isNpc = false;

    private String prompt;

    private String secret;

    private List<ObjectId> selfClueIds;

    private String locationTag;

    private Integer searchPower;
}

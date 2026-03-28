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
public class Role {
    @NotBlank
    private ObjectId id; // 在剧本内部唯一的 ID，如 "ROLE_001"
    @NotBlank
    private String name;
    private String avatar;
    private boolean isNpc;

    // --- AI 相关 ---
    private String prompt;    // AI 核心人设指令
    private String secret;    // 不可泄露的秘密

    // --- 游戏机制相关 ---
    private List<String> selfClueIds; // 角色自带的线索 ID
    private String locationTag;       // 该角色初始所在的地点（用于搜证）
    private int searchPower;          // 初始行动力
}

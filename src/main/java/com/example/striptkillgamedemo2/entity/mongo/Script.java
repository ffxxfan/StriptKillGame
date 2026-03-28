package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.ScriptDifficulty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

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
    private ObjectId id;

    @NotBlank
    private String title;
    private String description;
    private ScriptDifficulty difficulty;
    private int playerCount;
    private String coverImage;

    // --- 核心内嵌数据 ---

    // 1. 角色库：AI Agent 的灵魂都在这里
    @Size(min = 2, max = 15)
    private List<Role> roles;

    // 2. 线索库：存储所有静态线索定义
    private List<Clue> clues;

    // 3. 阶段流转：定义每一幕解锁什么，内容是什么
    private List<ScriptStage> stages;

    // --- 其他配置 ---
    private String dmConfig; // 存储 DM AI 的全局设定
    private int version = 1;
}
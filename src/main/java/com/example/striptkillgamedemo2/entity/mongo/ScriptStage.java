package com.example.striptkillgamedemo2.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ScriptStage represents a single stage within a script.
 * Embedded within Script for optimal query performance.
 *
 * ContentMap maps roleId (ObjectId) to content revealed to that role at this stage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptStage {
    private int stageNumber;
    private String stageTitle;

    /**
     * Key: roleId (角色的 ID)
     * Value: 该阶段该角色看到的剧本内容（包含 AI 需要知道的本幕任务）
     */
    @Builder.Default
    private Map<String, String> contentMap = new HashMap<>();

    private String audioUrl; // 本幕 BGM 或开场白

    /**
     * 重点：本阶段”解锁”的线索 ID 列表
     * 当游戏进入这一幕时，逻辑上将这些 ID 加入 GameRoom 的可搜索池
     */
    private List<String> unlockClueIds;

    /**
     * 本幕的阶段列表（如轮流发言、自由聊天、投票等）
     */
    private List<StagePhase> phases;
}
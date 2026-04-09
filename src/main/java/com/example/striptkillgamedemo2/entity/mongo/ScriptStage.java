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
 * 剧本阶段定义。
 * <p>
 * 内嵌于 {@link Script#getStages()}，描述剧本的某一"幕"（Stage），包含对不同角色可见的内容、
 * 本幕解锁的线索以及本幕内的子阶段流程。内嵌存储以保证一次查询即可取回剧本全部内容。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptStage {
    /** 幕编号（从 1 开始）。 */
    private int stageNumber;
    /** 幕标题。 */
    private String stageTitle;

    /**
     * 每个角色在本幕可见的剧情文本。
     * <p>
     * Key 为角色 ID，Value 为该角色看到的剧本内容（含 AI 扮演该角色时应知晓的本幕任务说明）。
     * </p>
     */
    @Builder.Default
    private Map<String, String> contentMap = new HashMap<>();

    /** 本幕 BGM 或开场白的音频 URL。 */
    private String audioUrl;

    /**
     * 本幕解锁的线索 ID 列表。
     * <p>
     * 当游戏进入本幕时，这些线索将被加入可搜索池，允许对应角色去发现它们。
     * </p>
     */
    private List<String> unlockClueIds;

    /** 本幕内的子阶段列表（轮流发言、自由讨论、投票等）。 */
    private List<StagePhase> phases;
}

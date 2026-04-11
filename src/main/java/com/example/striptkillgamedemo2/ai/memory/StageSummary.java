package com.example.striptkillgamedemo2.ai.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 幕次摘要数据模型。
 *
 * <p>由 {@link MemoryManager} 在每幕结束时通过 LLM 压缩生成，存储于 Redis 中，
 * 供后续幕次的提示词构建和最终复盘使用。包含关键事件、角色关系变化和未解之谜。</p>
 *
 * @see MemoryManager#compressStage(String, int, java.util.List)
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StageSummary {
    /** 幕次编号（0 起始） */
    private int stageNumber;
    /** 本幕关键事件列表 */
    private List<KeyEvent> keyEvents;
    /** 本幕角色关系变化列表 */
    private List<RelationshipChange> relationshipChanges;
    /** 本幕未解决的悬念 */
    private List<String> unresolved;

    /**
     * 关键事件。
     *
     * <p>记录一次重要的游戏事件，如指控、揭露线索、发表声明等。</p>
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class KeyEvent {
        /** 事件类型（如"指控"、"揭露"、"声明"等） */
        private String type;
        /** 事件发起者角色名 */
        private String from;
        /** 事件目标角色名 */
        private String to;
        /** 事件摘要描述 */
        private String summary;
    }

    /**
     * 角色关系变化。
     *
     * <p>记录两个角色之间的关系在本幕中发生的变化。</p>
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RelationshipChange {
        /** 关系变化的主动方角色名 */
        private String from;
        /** 关系变化的被动方角色名 */
        private String to;
        /** 关系变化描述 */
        private String change;
    }
}

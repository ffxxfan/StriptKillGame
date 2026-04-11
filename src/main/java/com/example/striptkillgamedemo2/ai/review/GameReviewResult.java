package com.example.striptkillgamedemo2.ai.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 游戏终局复盘结果数据模型。
 *
 * <p>由 {@link FinalReviewService} 在游戏结束时通过 LLM 生成，包含故事叙述、
 * 真相揭示、角色评分、未解之谜和 MVP 等内容，通过 WebSocket 广播给前端展示。</p>
 *
 * @see FinalReviewService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameReviewResult {
    /** 故事叙述回顾 */
    private String narrative;
    /** 真相揭示 */
    private String truthReveal;
    /** 各角色评分列表 */
    private List<RoleScore> roleScores;
    /** 未解决的谜团 */
    private List<String> unresolvedMysteries;
    /** 最有价值玩家 */
    private Mvp mvp;

    /**
     * 角色评分。
     *
     * <p>记录单个角色在本局游戏中的表现评价，包括得分、亮点和遗漏线索。</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleScore {
        /** 角色 ID */
        private String roleId;
        /** 角色名称 */
        private String roleName;
        /** 评分 */
        private int score;
        /** 表现亮点 */
        private List<String> highlights;
        /** 遗漏的线索 */
        private List<String> missedClues;
    }

    /**
     * 最有价值玩家（MVP）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Mvp {
        /** MVP 角色 ID */
        private String roleId;
        /** 获选原因 */
        private String reason;
    }
}

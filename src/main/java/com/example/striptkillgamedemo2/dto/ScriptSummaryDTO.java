package com.example.striptkillgamedemo2.dto;

import com.example.striptkillgamedemo2.entity.enums.ScriptDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 剧本摘要 DTO。
 * <p>
 * 用于剧本列表、房间创建时的剧本选择等场景，只携带展示必需的字段，避免返回完整剧本内容。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptSummaryDTO {
    /** 剧本 ID（MongoDB 主键）。 */
    private String id;
    /** 剧本标题。 */
    private String title;
    /** 剧本简介，用于列表展示。 */
    private String description;
    /** 剧本难度。 */
    private ScriptDifficulty difficulty;
    /** 所需玩家人数（含 DM 之外的角色数）。 */
    private int playerCount;
    /** 封面图 URL。 */
    private String coverImage;
}

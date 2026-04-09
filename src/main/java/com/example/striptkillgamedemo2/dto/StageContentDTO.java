package com.example.striptkillgamedemo2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * 阶段内容 DTO。
 * <p>
 * 推送给某个具体角色的当前剧本阶段内容。由于同一阶段对不同角色可见内容不同，
 * {@link #content} 会根据请求者角色从 {@code contentMap} 中按需取出。
 * </p>
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StageContentDTO {
    /** 阶段编号，从 1 开始。 */
    private int stageNumber;
    /** 阶段标题。 */
    private String stageTitle;
    /** 当前角色在该阶段可见的剧情文本；若该角色无对应内容则为 {@code null}。 */
    private String content;      // role-specific content (null if not in contentMap)
    /** 阶段配音或背景音 URL，可选。 */
    private String audioUrl;
    /** 剧本的阶段总数。 */
    private int totalStages;
    /** 是否为最后一个阶段。 */
    @JsonProperty("isLastStage")
    private boolean isLastStage;
}

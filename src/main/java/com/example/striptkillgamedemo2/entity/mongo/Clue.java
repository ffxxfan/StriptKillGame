package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.ClueType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * 线索定义。
 * <p>
 * 内嵌于 {@link Script#getClues()}，作为静态线索模板。游戏运行时会按此模板创建
 * {@link com.example.striptkillgamedemo2.entity.redis.GameClueInstance} 存放动态状态。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Clue {
    /** 线索 ID。 */
    private ObjectId id;
    /** 线索标题。 */
    private String title;
    /** 线索媒体类型：{@code TEXT}、{@code IMAGE}、{@code VIDEO}。 */
    private ClueType type;
    /** 文本线索内容（当 {@link #type} 为 {@link ClueType#TEXT} 时有效）。 */
    private String content;
    /** 图片线索的图片 URL（当 {@link #type} 为 {@link ClueType#IMAGE} 时有效）。 */
    private String imageUrl;

    // 控制逻辑
    /** 是否初始隐藏；初始隐藏的线索需通过搜证发现。 */
    @JsonProperty("isInitialHidden")
    private boolean isInitialHidden = true;
    /** 可搜索到此线索的角色 ID 列表。 */
    private List<String> searchableRoleIds;
    /** 线索所处的地点标签（可能有多个位置）。 */
    private List<String> locationTag;
    /** 允许发现此线索的阶段编号；{@code null} 或空表示不限阶段。 */
    private List<Integer> stages;
    /** 可见性：{@code PUBLIC} 或 {@code PRIVATE}，{@code null} 时默认 {@code PUBLIC}。 */
    private String visibility;
}

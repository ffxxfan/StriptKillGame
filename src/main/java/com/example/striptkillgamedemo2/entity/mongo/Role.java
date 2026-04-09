package com.example.striptkillgamedemo2.entity.mongo;

import com.fasterxml.jackson.annotation.JsonProperty;
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
 * 剧本角色定义。
 * <p>
 * 内嵌于 {@link Script#getRoles()}，描述某一角色的公共信息、AI 人设与游戏机制属性。
 * </p>
 *
 * <p><b>线索-角色关系说明：</b>线索可被哪些角色搜到，以 {@link Clue#getSearchableRoleIds()}
 * 为准；本类的 {@link #selfClueIds} 用于便捷查询，应当与反向查询结果保持一致，系统在
 * 游戏开始时会做一致性校验。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    /** 角色 ID，在剧本内部唯一。 */
    @NotBlank
    private ObjectId id;
    /** 角色姓名。 */
    @NotBlank
    private String name;
    /** 角色头像 URL。 */
    private String avatar;
    /** 是否为 NPC（由 AI 扮演）。 */
    @JsonProperty("isNpc")
    private boolean isNpc;

    // --- AI 相关 ---
    /** AI 核心人设 Prompt 指令。 */
    private String prompt;
    /** 角色秘密，禁止向其他玩家泄露。 */
    private String secret;

    // --- 游戏机制相关 ---
    /** 角色自带可搜索到的线索 ID 列表。 */
    private List<String> selfClueIds;
    /** 角色初始所在的地点标签，用于搜证定位。 */
    private String locationTag;
    /** 初始行动力（可搜证次数）。 */
    private int searchPower;
}

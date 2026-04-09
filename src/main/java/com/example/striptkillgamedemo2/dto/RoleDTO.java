package com.example.striptkillgamedemo2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 剧本角色 DTO。
 * <p>
 * 用于前端展示可选/已选角色列表，不包含背景故事、秘密等敏感字段。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {
    /** 角色 ID。 */
    private String id;
    /** 角色名称。 */
    private String name;
    /** 角色头像 URL。 */
    private String avatar;
    /** 是否为 AI 托管角色；为 {@code true} 时表示由 AI 自动扮演。 */
    @JsonProperty("isNpc")
    private boolean isNpc;
    /** 当前角色是否可被玩家选择（未被占用）。 */
    @JsonProperty("isAvailable")
    private boolean isAvailable;
}

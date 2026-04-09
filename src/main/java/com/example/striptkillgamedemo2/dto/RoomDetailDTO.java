package com.example.striptkillgamedemo2.dto;

import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 房间详情 DTO。
 * <p>
 * 返回房间基础信息及成员列表，用于房间详情页、游戏内成员栏等场景。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomDetailDTO {
    /** 房间 ID。 */
    private String roomId;
    /** 房间所用剧本 ID。 */
    private String scriptId;
    /** 剧本标题，便于前端直接展示。 */
    private String scriptTitle;
    /** 房间当前状态。 */
    private GameRoomStatus status;
    /** 当前已推进到的阶段编号。 */
    private int currentStage;
    /** 房间成员列表（含真人与 AI 角色）。 */
    private List<MemberDTO> members;

    /**
     * 房间成员 DTO。
     * <p>
     * 描述某一具体席位的占用情况，包括玩家身份与在线状态。
     * </p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberDTO {
        /** 玩家用户 ID；若为 AI 席位可能为 {@code null}。 */
        private String userId;
        /** 角色 ID。 */
        private String roleId;
        /** 角色名称。 */
        private String roleName;
        /** 角色头像 URL。 */
        private String roleAvatar;
        /** 是否由 AI 托管。 */
        @JsonProperty("isAi")
        private boolean isAi;
        /** 是否为 DM（主持人）。 */
        @JsonProperty("isDm")
        private boolean isDm;
        /** 当前是否在线。 */
        @JsonProperty("isOnline")
        private boolean isOnline;
    }
}

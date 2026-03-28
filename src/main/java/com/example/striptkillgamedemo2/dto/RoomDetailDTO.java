package com.example.striptkillgamedemo2.dto;

import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomDetailDTO {
    private String roomId;
    private String scriptId;
    private String scriptTitle;
    private GameRoomStatus status;
    private int currentStage;
    private List<MemberDTO> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberDTO {
        private String userId;
        private String roleId;
        private String roleName;
        private String roleAvatar;
        private boolean isAi;
        private boolean isDm;
        private boolean isOnline;
    }
}

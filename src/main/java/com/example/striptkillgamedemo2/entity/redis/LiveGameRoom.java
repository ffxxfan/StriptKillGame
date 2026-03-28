package com.example.striptkillgamedemo2.entity.redis;

import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime game room state stored in Redis.
 * Source of truth for all active (PLAYING) game state.
 *
 * Key format: game:room:{roomId}
 * TTL: 12h while active, reduced to 2h when all human players go offline.
 *
 * Members and ClueInstances live here during gameplay.
 * Messages live in the companion list key: game:messages:{roomId}
 * MongoDB GameRoom is updated only at milestones (start / stage advance / end).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveGameRoom {

    private String roomId;
    private String scriptId;
    private GameRoomStatus status;
    private int currentStage;

    @Builder.Default
    private List<Member> members = new ArrayList<>();

    @Builder.Default
    private List<GameClueInstance> clueInstances = new ArrayList<>();

    private LocalDateTime lastActiveAt;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String configuration;
}

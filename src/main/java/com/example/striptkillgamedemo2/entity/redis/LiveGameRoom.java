package com.example.striptkillgamedemo2.entity.redis;

import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.redis.VoteSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 运行时游戏房间状态（Redis 数据结构）。
 * <p>
 * 作为 {@link GameRoomStatus#PLAYING} 状态下房间的"唯一可信来源"，承载所有高频变化的
 * 游戏状态。Key 格式为 {@code game:room:{roomId}}；存活时 TTL 为 12 小时，当所有真人玩家
 * 离线时缩短为 2 小时。
 * </p>
 *
 * <p>成员列表、线索实例均内嵌于此；消息则存储在配对的列表 Key
 * {@code game:messages:{roomId}} 中。对 MongoDB {@code GameRoom} 的写入仅在里程碑时刻
 * （游戏开始 / 阶段推进 / 游戏结束）发生。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveGameRoom {

    /** 房间 ID（字符串形式）。 */
    private String roomId;
    /** 所用剧本 ID。 */
    private String scriptId;
    /** 房间状态。 */
    private GameRoomStatus status;
    /** 当前阶段编号。 */
    private int currentStage;

    /** 当前成员列表。 */
    @Builder.Default
    private List<Member> members = new ArrayList<>();

    /** 当前房间的线索运行时实例列表。 */
    @Builder.Default
    private List<GameClueInstance> clueInstances = new ArrayList<>();

    /** 房间最近活跃时间，用于 TTL 续期判断。 */
    private LocalDateTime lastActiveAt;
    /** 游戏开始时间。 */
    private LocalDateTime startTime;
    /** 游戏结束时间。 */
    private LocalDateTime endTime;
    /** 游戏配置 JSON 文本。 */
    private String configuration;

    /** 当前子阶段索引（指向 {@code ScriptStage.phases} 中的位置）。 */
    private int currentPhaseIndex;
    /** 当前轮流发言阶段中正在发言的角色 ID。 */
    private String currentSpeakerRoleId;
    /** 当前活跃的投票会话；无则为 {@code null}。 */
    private VoteSession activeVote;

    /** 本子阶段已发言过的角色 ID 集合。 */
    @Builder.Default
    private Set<String> spokenRoleIds = new HashSet<>();

    /** 已被淘汰（出局）的角色 ID 集合。 */
    @Builder.Default
    private Set<String> eliminatedRoleIds = new HashSet<>();

    /** 当前子阶段是否已超时进入延长时间。 */
    private boolean phaseOvertime;

    /** 调查模式：{@code PUBLIC} 或 {@code PRIVATE}。 */
    private String investigationMode;

    /** 投票子阶段：{@code STATEMENT} / {@code VOTING} / {@code RESULT}。 */
    private String voteSubPhase;

    /** 仍待进行自我介绍的真人玩家角色 ID 集合（十六进制字符串）。 */
    @Builder.Default
    private Set<String> awaitingIntroRoleIds = new HashSet<>();
}

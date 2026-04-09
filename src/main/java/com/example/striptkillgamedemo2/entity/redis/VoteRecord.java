package com.example.striptkillgamedemo2.entity.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;

/**
 * 投票记录。
 * <p>
 * 存放于 Redis 列表 {@code game:{roomId}:votes}，每条记录代表一次投票行为，用于审计与复盘。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteRecord {
    /** 投票记录 ID。 */
    private ObjectId id;
    /** 所属房间 ID。 */
    private ObjectId gameRoomId;
    /** 投票者用户 ID。 */
    private ObjectId voterUserId;
    /** 当前阶段编号。 */
    private int stageNumber;
    /** 被投的目标角色 ID。 */
    private ObjectId votedRoleId;
    /** 投票类别，可选。 */
    private String voteCategory;
    /** 投票理由，可选。 */
    private String voteReason;
    /** 投票权重，用于加权投票，可选。 */
    private Integer voteWeight;
    /** 投票时间戳。 */
    private LocalDateTime timestamp;
}

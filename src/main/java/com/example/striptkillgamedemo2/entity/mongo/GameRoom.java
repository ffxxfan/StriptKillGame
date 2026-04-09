package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 游戏房间持久化实体。
 * <p>
 * 对应 MongoDB 集合 {@code game_rooms}，作为房间的"里程碑快照"存在：
 * 仅在游戏开始、阶段推进、游戏结束等关键时刻更新；运行时状态请参考
 * {@link com.example.striptkillgamedemo2.entity.redis.LiveGameRoom}。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "game_rooms")
public class GameRoom {
    /** 房间主键。 */
    @Id
    private ObjectId roomId;
    /** 所用剧本 ID。 */
    private ObjectId scriptId;
    /** 房间当前状态。 */
    private GameRoomStatus status;
    /** 当前阶段编号。 */
    private int currentStage;

    /** 房间成员列表。 */
    @Builder.Default
    private List<Member> members = new ArrayList<>();

    /** 游戏开始时间。 */
    private LocalDateTime startTime;
    /** 游戏结束时间。 */
    private LocalDateTime endTime;
    /** 游戏配置（JSON 文本），可覆盖剧本默认配置。 */
    private String configuration;
}

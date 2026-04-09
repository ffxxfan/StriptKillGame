package com.example.striptkillgamedemo2.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 游戏记录实体。
 * <p>
 * 对应 MongoDB 集合 {@code game_records}，用于在游戏结束后创建并持久化一场游戏的战报/总结，
 * 供历史回顾、复盘与数据分析使用。运行时状态请参考 {@code LiveGameRoom}。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "game_records")
public class GameRecord {
    /** 记录主键。 */
    @Id
    private ObjectId recordId;

    /** 关联的游戏房间 ID。 */
    @Indexed
    private ObjectId roomId;

    /** 剧本标题，冗余保存便于历史展示。 */
    private String scriptTitle;

    /** 获胜玩家用户 ID 列表。 */
    private List<ObjectId> winnerUserIds;

    /** 获胜角色 ID 列表。 */
    private List<ObjectId> winnerRoleIds;

    /** AI 生成的复盘总结文本。 */
    private String aiSummary;

    /** 精选保留的关键对话日志。 */
    private List<String> fullChatLog;

    /** 游戏开始时间。 */
    private LocalDateTime startTime;

    /** 游戏结束时间。 */
    private LocalDateTime endTime;
}

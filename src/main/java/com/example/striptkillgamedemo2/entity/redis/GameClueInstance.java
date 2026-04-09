package com.example.striptkillgamedemo2.entity.redis;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 线索运行时实例。
 * <p>
 * 对应一个具体游戏房间中某条线索的动态状态，存放于 Redis，键格式为
 * {@code game:{roomId}:clue:{clueId}}。游戏结束后不再持久化。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameClueInstance {
    /** Redis 主键。 */
    @NotBlank
    private ObjectId id;
    /** 所引用的线索模板 ID。 */
    @NotBlank
    private ObjectId clueId;
    /** 当前持有此线索的角色 ID 列表（可多人持有）。 */
    private List<ObjectId> ownerRoleIds;
    /** 是否已对所有玩家公开。 */
    @JsonProperty("isPublic")
    private boolean isPublic;
    /** 是否已被发现。 */
    @JsonProperty("isFound")
    private boolean isFound = false;
    /** 被发现的时间（审计用）。 */
    private LocalDateTime discoveredAt;
}

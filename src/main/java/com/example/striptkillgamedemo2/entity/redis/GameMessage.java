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
 * 游戏聊天消息。
 * <p>
 * 存放于 Redis 列表 {@code game:messages:{roomId}}，承载游戏过程中的玩家、AI 与 DM 发言。
 * 运行期即时性强，游戏结束后由归档任务择要持久化到 {@code GameRecord}。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage {
    /** 消息 ID。 */
    @NotBlank
    private ObjectId messageId;
    /** 所属房间 ID。 */
    @NotBlank
    private ObjectId gameRoomId;
    /** 发送者角色 ID。 */
    @NotBlank
    private ObjectId senderRoleId;
    /** 是否由 AI 代理发送。 */
    @JsonProperty("isAi")
    private boolean isAi;
    /** 发送者角色名，冗余存储以避免查询。 */
    private String senderRoleName;
    /** 发送者头像 URL。 */
    private String senderAvatar;
    /** 消息内容。 */
    private String content;
    /** 接收者角色 ID 列表；为空或 null 表示房间内广播。 */
    private List<ObjectId> receiverRoleIds;
    /** 服务端时间戳。 */
    private LocalDateTime timestamp;
}

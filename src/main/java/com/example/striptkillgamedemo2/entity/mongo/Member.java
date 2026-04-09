package com.example.striptkillgamedemo2.entity.mongo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * 房间成员内嵌实体。
 * <p>
 * 作为 {@link GameRoom#getMembers()} 的内嵌元素存在，描述某个席位的占用者信息
 * （真人玩家或 AI / DM），以及其在线状态。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {
    /** 玩家用户 ID；AI 角色时为 {@code null}。 */
    private ObjectId userId;
    /** 所扮演的角色 ID。 */
    private ObjectId roleId;
    /** 是否为 AI 托管。 */
    @Field("isAi")
    @JsonProperty("isAi")
    private boolean isAi = false;
    /** 是否为 DM（主持人）。 */
    @Field("isDm")
    @JsonProperty("isDm")
    private boolean isDm = false;
    /** 是否处于在线状态，用于实时功能。 */
    @Field("isOnline")
    @JsonProperty("isOnline")
    private boolean isOnline = true;
    /** 若为 AI 角色，可在此记录其扮演的性格/风格描述。 */
    private String description;
}

package com.example.striptkillgamedemo2.entity.mongo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 用户实体。
 * <p>
 * 对应 MongoDB 集合 {@code users}，用于存储玩家账号信息。
 * </p>
 *
 * <p><b>安全说明：</b></p>
 * <ul>
 *     <li>{@link #password} 存储的是 BCrypt 加密后的哈希值，请务必在服务层使用
 *     {@code BCryptPasswordEncoder} 进行加密与校验。</li>
 *     <li>{@link #password} 通过 {@link JsonIgnore} 从 JSON 序列化中排除，避免泄露。</li>
 *     <li>{@link #username} 建立唯一索引用于登录鉴权。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {
    /** 用户主键 ID。 */
    @Id
    private ObjectId id;

    /** 登录用户名，唯一索引。 */
    @Indexed(unique = true)
    @NotBlank
    private String username;

    /** 密码哈希（BCrypt），不参与 JSON 序列化。 */
    @NotBlank
    @JsonIgnore
    private String password;

    /** 昵称，用于前端展示。 */
    private String nickname;
    /** 头像 URL。 */
    private String avatarUrl;

    /** 账号创建时间，由 Spring Data 的审计功能自动填充。 */
    @org.springframework.data.annotation.CreatedDate
    private LocalDateTime createdAt;
}

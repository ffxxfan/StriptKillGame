package com.example.striptkillgamedemo2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户信息响应 DTO。
 * <p>
 * 对外返回当前登录用户的公共信息（不含密码哈希等敏感字段）。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    /** 用户 ID（MongoDB 主键）。 */
    private String id;
    /** 登录用户名。 */
    private String username;
    /** 昵称，前端展示用。 */
    private String nickname;
    /** 头像 URL。 */
    private String avatarUrl;
    /** 账号创建时间。 */
    private LocalDateTime createdAt;
}

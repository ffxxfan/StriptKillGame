package com.example.striptkillgamedemo2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求 DTO。
 * <p>
 * 封装用户在已登录状态下修改密码所需的入参。旧密码用于校验身份，新密码用于替换。
 * </p>
 */
@Data
public class ChangePasswordRequest {
    /** 旧密码（明文），服务端需校验与当前数据库密码一致。 */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码（明文），长度 6~50，服务端会使用 BCrypt 加密后存储。 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 50, message = "新密码长度需在6-50之间")
    private String newPassword;
}

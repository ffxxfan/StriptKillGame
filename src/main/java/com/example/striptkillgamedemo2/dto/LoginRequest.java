package com.example.striptkillgamedemo2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO。
 * <p>
 * 封装用户名密码登录接口所需的入参，由控制器层 {@code @Valid} 触发非空校验。
 * </p>
 */
@Data
public class LoginRequest {
    /** 登录用户名，不能为空。 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 登录密码（明文），由服务端校验后与数据库密码哈希比对。 */
    @NotBlank(message = "密码不能为空")
    private String password;
}

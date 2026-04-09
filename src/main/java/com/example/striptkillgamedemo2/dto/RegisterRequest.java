package com.example.striptkillgamedemo2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求 DTO。
 * <p>
 * 封装用户注册接口的入参。用户名与密码由 Bean Validation 做长度校验；昵称可选，
 * 未提供时服务端会使用默认值填充。
 * </p>
 */
@Data
public class RegisterRequest {
    /** 用户名，长度 3~20，注册后不可更改。 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需在3-20之间")
    private String username;

    /** 登录密码（明文），长度 6~50，服务端会使用 BCrypt 加密后存储。 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度需在6-50之间")
    private String password;

    /** 昵称，可选；为空时由服务端使用用户名或默认值填充。 */
    private String nickname;
}

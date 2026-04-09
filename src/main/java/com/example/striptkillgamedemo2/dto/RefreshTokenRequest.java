package com.example.striptkillgamedemo2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 Token 请求 DTO。
 * <p>
 * 封装调用刷新接口时客户端提交的 Refresh Token。
 * </p>
 */
@Data
public class RefreshTokenRequest {
    /** 需要换取新 Access Token 的刷新令牌。 */
    @NotBlank(message = "Refresh Token 不能为空")
    private String refreshToken;
}

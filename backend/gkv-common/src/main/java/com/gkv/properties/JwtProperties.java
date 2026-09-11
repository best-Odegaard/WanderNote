package com.gkv.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.jwt")
@Data
public class JwtProperties {
    // 管理端jwt密钥
    private String adminSecretKey;
    // 管理端jwt令牌生成时间
    private long adminTtl;
    // 管理端jwt令牌在前端存储的名称
    private String adminTokenName;

    // 用户端jwt密钥
    private String userSecretKey;
    // 用户端jwt令牌生成时间
    private long userTtl;
    // 用户端jwt令牌在前端存储的名称
    private String userTokenName;
}


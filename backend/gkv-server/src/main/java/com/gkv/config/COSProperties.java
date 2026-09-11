package com.gkv.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "cos")
public class COSProperties {
    /**
     * 访问域名
     */
    @Value("${sky.cos.domain}")
    private String domain;

    /**
     * 桶名称
     */
    @Value("${sky.cos.bucketName}")
    private String bucketName;

    /**
     * api密钥中的secretId
     */
    @Value("${sky.cos.secretId}")
    private String secretId;

    /**
     * api密钥中的应用密钥
     */
    @Value("${sky.cos.secretKey}")
    private String secretKey;

    @Value("${sky.cos.regionName}")
    private String regionName;
}

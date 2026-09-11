package com.gkv.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosClientConfig {

    @Autowired
    private COSProperties cosProperties;

    @Bean
    public COSClient cosClient() {
        // 从配置文件中获取secretId和secretKey
        COSCredentials cred = new BasicCOSCredentials(cosProperties.getSecretId(), cosProperties.getSecretKey());

        // 设置bucket的区域
        Region region = new Region(cosProperties.getRegionName());
        ClientConfig clientConfig = new ClientConfig(region);

        // 生成cos客户端
        return new COSClient(cred, clientConfig);
    }
}

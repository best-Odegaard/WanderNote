package com.gkv.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component  //获取容器所需对象
@ConfigurationProperties(prefix = "sky.alioss")
@Data
public class AliOssProperties { //配置属性类  5个

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

}

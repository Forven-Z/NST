package com.hospital.pacs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hospital.minio")
public class MinioProperties {

    private String endpoint = "http://127.0.0.1:9001";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin123";
    private String bucket = "imaging";
}

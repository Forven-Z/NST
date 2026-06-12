package com.hospital.pacs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hospital.ai")
public class HospitalAiProperties {

    private String baseUrl = "http://127.0.0.1:8000";
    private String callbackUrl = "http://127.0.0.1:9104/internal/imaging/callback";
    private long inferenceTimeoutSeconds = 180;
}

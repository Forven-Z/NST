package com.hospital.lis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hospital.ai-bridge")
public class AiBridgeProperties {

    private String baseUrl = "http://127.0.0.1:9106";
    private int reportTimeoutSeconds = 180;
}

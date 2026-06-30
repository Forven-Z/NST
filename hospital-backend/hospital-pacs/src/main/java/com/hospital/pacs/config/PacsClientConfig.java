package com.hospital.pacs.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class PacsClientConfig {

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder, AiBridgeProperties aiBridgeProperties) {
        Duration reportTimeout = Duration.ofSeconds(aiBridgeProperties.getReportTimeoutSeconds());
        return builder
                .setConnectTimeout(reportTimeout)
                .setReadTimeout(reportTimeout)
                .build();
    }
}

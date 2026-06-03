package com.hospital.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "hospital.auth")
public class AuthProperties {

    private Jwt jwt = new Jwt();
    private Internal internal = new Internal();

    @Data
    public static class Jwt {
        private String secret;
        private long accessTokenExpireSeconds = 7200;
        private long refreshTokenExpireSeconds = 604800;
    }

    @Data
    public static class Internal {
        private String header = "X-Internal-Service";
        private List<String> allowedServices = new ArrayList<>();
    }
}

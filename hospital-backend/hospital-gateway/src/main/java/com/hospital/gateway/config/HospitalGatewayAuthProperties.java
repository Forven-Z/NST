package com.hospital.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "hospital.gateway")
public class HospitalGatewayAuthProperties {

    private Jwt jwt = new Jwt();
    private List<WhitelistRule> whitelist = defaultWhitelist();

    @Data
    public static class Jwt {
        private String secret;
    }

    @Data
    public static class WhitelistRule {
        private String path;
        private List<String> methods = new ArrayList<>();
    }

    private static List<WhitelistRule> defaultWhitelist() {
        return List.of(
                rule("POST", "/api/v1/auth/staff/login"),
                rule("POST", "/api/v1/auth/token/refresh"),
                rule("GET", "/api/v1/auth/health"),
                rule("POST", "/api/v1/patient/auth/login"),
                rule("POST", "/api/v1/patient/auth/wechat"),
                rule("POST", "/api/v1/callback/wechat/pay")
        );
    }

    private static WhitelistRule rule(String method, String path) {
        WhitelistRule r = new WhitelistRule();
        r.setPath(path);
        r.setMethods(List.of(method));
        return r;
    }
}

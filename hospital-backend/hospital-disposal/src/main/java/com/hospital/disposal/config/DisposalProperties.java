package com.hospital.disposal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hospital")
public class DisposalProperties {

    private Auth auth = new Auth();

    @Data
    public static class Auth {
        private Jwt jwt = new Jwt();
    }

    @Data
    public static class Jwt {
        private String secret;
    }
}

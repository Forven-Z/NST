package com.hospital.pharmacy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hospital")
public class PharmacyProperties {

    private Auth auth = new Auth();
    private Internal internal = new Internal();

    @Data
    public static class Auth {
        private Jwt jwt = new Jwt();
    }

    @Data
    public static class Jwt {
        private String secret;
    }

    @Data
    public static class Internal {
        private String serviceName = "hospital-pharmacy";
    }
}

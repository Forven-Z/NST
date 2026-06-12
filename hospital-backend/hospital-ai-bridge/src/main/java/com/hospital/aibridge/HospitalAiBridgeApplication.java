package com.hospital.aibridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.hospital.aibridge", "com.hospital.common"})
@ConfigurationPropertiesScan(basePackages = "com.hospital.aibridge.config")
@EnableScheduling
public class HospitalAiBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalAiBridgeApplication.class, args);
    }
}

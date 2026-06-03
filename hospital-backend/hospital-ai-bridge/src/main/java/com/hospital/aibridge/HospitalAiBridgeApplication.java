package com.hospital.aibridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.hospital.aibridge", "com.hospital.common"})
public class HospitalAiBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalAiBridgeApplication.class, args);
    }
}

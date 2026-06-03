package com.hospital.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.hospital.auth", "com.hospital.common"})
public class HospitalAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalAuthApplication.class, args);
    }
}

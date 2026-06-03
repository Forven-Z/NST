package com.hospital.lis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.hospital.lis", "com.hospital.common"})
public class HospitalLisApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalLisApplication.class, args);
    }
}

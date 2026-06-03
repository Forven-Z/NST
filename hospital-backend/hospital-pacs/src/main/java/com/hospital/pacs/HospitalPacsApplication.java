package com.hospital.pacs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.hospital.pacs", "com.hospital.common"})
public class HospitalPacsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalPacsApplication.class, args);
    }
}

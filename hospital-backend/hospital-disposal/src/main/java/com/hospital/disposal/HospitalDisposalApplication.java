package com.hospital.disposal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.hospital.disposal", "com.hospital.common"})
public class HospitalDisposalApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalDisposalApplication.class, args);
    }
}

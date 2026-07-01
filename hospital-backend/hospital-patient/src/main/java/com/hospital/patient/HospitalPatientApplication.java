package com.hospital.patient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.hospital.patient", "com.hospital.common"})
@EnableFeignClients
@EnableScheduling
public class HospitalPatientApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalPatientApplication.class, args);
    }
}

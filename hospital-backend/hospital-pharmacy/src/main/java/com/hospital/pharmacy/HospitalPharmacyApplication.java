package com.hospital.pharmacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.hospital.pharmacy", "com.hospital.common"})
@EnableFeignClients
public class HospitalPharmacyApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalPharmacyApplication.class, args);
    }
}

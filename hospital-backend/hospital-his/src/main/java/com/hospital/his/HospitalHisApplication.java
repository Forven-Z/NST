package com.hospital.his;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.hospital.his", "com.hospital.common"})
@EnableFeignClients
public class HospitalHisApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalHisApplication.class, args);
    }
}

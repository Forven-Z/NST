package com.hospital.his;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.hospital.his", "com.hospital.common"})
@EnableFeignClients
@EnableScheduling
public class HospitalHisApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalHisApplication.class, args);
    }
}

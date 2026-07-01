package com.hospital.patient.client;

import com.hospital.common.Result;
import com.hospital.patient.client.dto.PatientTokenFeignRequest;
import com.hospital.patient.client.dto.PatientTokenFeignResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = AuthTokenFeignClient.SERVICE_NAME)
public interface AuthTokenFeignClient {

    String SERVICE_NAME = "hospital-auth";

    @PostMapping("/internal/token/patient")
    Result<PatientTokenFeignResponse> issuePatientToken(@RequestBody PatientTokenFeignRequest request);
}

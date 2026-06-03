package com.hospital.his.client;

import com.hospital.common.Result;
import com.hospital.his.client.dto.PatientTokenFeignRequest;
import com.hospital.his.client.dto.PatientTokenFeignResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = AuthTokenFeignClient.SERVICE_NAME)
public interface AuthTokenFeignClient {

    String SERVICE_NAME = "hospital-auth";

    @PostMapping("/internal/token/patient")
    Result<PatientTokenFeignResponse> issuePatientToken(@RequestBody PatientTokenFeignRequest request);
}

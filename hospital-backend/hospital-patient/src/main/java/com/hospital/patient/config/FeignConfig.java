package com.hospital.patient.config;

import com.hospital.patient.client.AuthTokenFeignClient;
import com.hospital.patient.client.ClinicalOrderFeignClient;
import com.hospital.patient.client.PacsImagingFeignClient;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor internalServiceHeaderInterceptor(PatientProperties patientProperties) {
        return template -> {
            if (template.feignTarget() == null) {
                return;
            }
            String target = template.feignTarget().name();
            if (AuthTokenFeignClient.SERVICE_NAME.equals(target)
                    || ClinicalOrderFeignClient.SERVICE_NAME.equals(target)
                    || PacsImagingFeignClient.SERVICE_NAME.equals(target)) {
                template.header("X-Internal-Service", patientProperties.getInternal().getServiceName());
            }
        };
    }
}

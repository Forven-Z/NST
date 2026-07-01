package com.hospital.pharmacy.config;

import com.hospital.pharmacy.client.PatientRefundFeignClient;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor internalServiceHeaderInterceptor(PharmacyProperties pharmacyProperties) {
        return template -> {
            if (template.feignTarget() != null
                    && PatientRefundFeignClient.SERVICE_NAME.equals(template.feignTarget().name())) {
                template.header("X-Internal-Service", pharmacyProperties.getInternal().getServiceName());
            }
        };
    }
}

package com.hospital.his.config;

import com.hospital.his.client.PatientBillFeignClient;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor internalServiceHeaderInterceptor(HisProperties hisProperties) {
        return template -> {
            if (template.feignTarget() != null
                    && PatientBillFeignClient.SERVICE_NAME.equals(template.feignTarget().name())) {
                template.header("X-Internal-Service", hisProperties.getInternal().getServiceName());
            }
        };
    }
}

package com.hospital.management.config;

import com.hospital.management.client.AuthStaffFeignClient;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor internalServiceHeaderInterceptor(ManagementProperties managementProperties) {
        return template -> {
            if (template.feignTarget() != null
                    && AuthStaffFeignClient.SERVICE_NAME.equals(template.feignTarget().name())) {
                template.header("X-Internal-Service", managementProperties.getInternal().getServiceName());
            }
        };
    }
}

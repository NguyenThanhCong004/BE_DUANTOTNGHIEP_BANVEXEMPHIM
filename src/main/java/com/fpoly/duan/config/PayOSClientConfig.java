package com.fpoly.duan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PayOSClientConfig {

    @Bean
    public RestClient payOSRestClient(PayOSProperties props) {
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("x-client-id", props.getClientId())
                .defaultHeader("x-api-key", props.getApiKey())
                .build();
    }
}

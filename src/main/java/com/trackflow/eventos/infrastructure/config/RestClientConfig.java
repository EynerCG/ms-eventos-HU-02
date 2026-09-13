package com.trackflow.eventos.infrastructure.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClientMsEnvios(
            @Value("${trackflow.ms-envios.base-url}") String baseUrl,
            @Value("${trackflow.ms-envios.timeout-conexion-ms:3000}") long timeoutConexionMs,
            @Value("${trackflow.ms-envios.timeout-lectura-ms:5000}") long timeoutLecturaMs) {

        ClientHttpRequestFactorySettings ajustes = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(timeoutConexionMs))
                .withReadTimeout(Duration.ofMillis(timeoutLecturaMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(ajustes))
                .build();
    }
}

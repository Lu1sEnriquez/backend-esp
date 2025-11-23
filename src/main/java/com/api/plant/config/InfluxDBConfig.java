package com.api.plant.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.InfluxDBClientOptions; // Importar esto
import okhttp3.OkHttpClient; // Importar esto
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class InfluxDBConfig {

    @Value("${influxdb.url}")
    private String url;

    @Value("${influxdb.token}")
    private String token;

    @Value("${influxdb.org}")
    private String org;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Bean
    public InfluxDBClient influxDBClient() {
        // Configuración avanzada para aumentar el Timeout
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .readTimeout(Duration.ofSeconds(60))  // Aumentar a 60s (default es 10s)
                .writeTimeout(Duration.ofSeconds(60)) // Aumentar a 60s
                .connectTimeout(Duration.ofSeconds(60)); // Aumentar a 60s

        InfluxDBClientOptions options = InfluxDBClientOptions.builder()
                .url(url)
                .authenticateToken(token.toCharArray())
                .org(org)
                .bucket(bucket)
                .okHttpClient(okHttpClientBuilder) // Aplicar timeouts
                .build();

        return InfluxDBClientFactory.create(options);
    }
}
package com.agile.paybot.config;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("gcp")
public class GcpTracingConfig {

    @Bean
    public SpanExporter spanExporter(
            @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}") String endpoint) {
        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .build();
    }

    @Bean
    public TextMapPropagator textMapPropagator() {
        return W3CTraceContextPropagator.getInstance();
    }
}

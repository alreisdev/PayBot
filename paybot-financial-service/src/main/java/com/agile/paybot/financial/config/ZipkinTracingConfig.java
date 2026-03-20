package com.agile.paybot.financial.config;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!gcp")
public class ZipkinTracingConfig {

    @Bean
    public SpanExporter spanExporter(
            @Value("${management.zipkin.tracing.endpoint:http://localhost:9411/api/v2/spans}") String endpoint) {
        return ZipkinSpanExporter.builder()
                .setEndpoint(endpoint)
                .build();
    }

    @Bean
    public TextMapPropagator textMapPropagator() {
        return B3Propagator.injectingMultiHeaders();
    }
}

package com.agile.paybot.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.otel.bridge.Slf4JBaggageEventListener;
import io.micrometer.tracing.otel.bridge.Slf4JEventListener;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ServiceAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class TracingConfig {

    private final ObservationRegistry observationRegistry;

    public TracingConfig(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Bean
    public SdkTracerProvider sdkTracerProvider(SpanExporter spanExporter,
            @Value("${spring.application.name:paybot-ai-service}") String serviceName) {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, serviceName)));
        return SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(Sampler.alwaysOn())
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .build();
    }

    @Bean
    public OpenTelemetry openTelemetry(SdkTracerProvider sdkTracerProvider, TextMapPropagator textMapPropagator) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(textMapPropagator))
                .build();
    }

    @Bean
    public OtelCurrentTraceContext otelCurrentTraceContext() {
        return new OtelCurrentTraceContext();
    }

    @Bean
    public io.opentelemetry.api.trace.Tracer otelApiTracer(OpenTelemetry openTelemetry,
            @Value("${spring.application.name:paybot-ai-service}") String serviceName) {
        return openTelemetry.getTracer(serviceName);
    }

    @Bean
    public Tracer micrometerTracer(io.opentelemetry.api.trace.Tracer otelApiTracer,
            OtelCurrentTraceContext otelCurrentTraceContext) {
        Slf4JEventListener slf4JEventListener = new Slf4JEventListener();
        Slf4JBaggageEventListener slf4JBaggageEventListener = new Slf4JBaggageEventListener(Collections.emptyList());
        return new OtelTracer(otelApiTracer, otelCurrentTraceContext, event -> {
            slf4JEventListener.onEvent(event);
            slf4JBaggageEventListener.onEvent(event);
        });
    }

    @Bean
    public Propagator micrometerPropagator(OpenTelemetry openTelemetry,
            io.opentelemetry.api.trace.Tracer otelApiTracer) {
        return new OtelPropagator(openTelemetry.getPropagators(), otelApiTracer);
    }

    @Bean
    public TracingObservationHandlerRegistrar tracingHandlerRegistrar(
            Tracer tracer, Propagator propagator) {
        return new TracingObservationHandlerRegistrar(observationRegistry, tracer, propagator);
    }

    static class TracingObservationHandlerRegistrar {
        TracingObservationHandlerRegistrar(ObservationRegistry registry, Tracer tracer, Propagator propagator) {
            registry.observationConfig()
                    .observationHandler(new PropagatingSenderTracingObservationHandler<>(tracer, propagator))
                    .observationHandler(new PropagatingReceiverTracingObservationHandler<>(tracer, propagator))
                    .observationHandler(new DefaultTracingObservationHandler(tracer));
        }
    }
}

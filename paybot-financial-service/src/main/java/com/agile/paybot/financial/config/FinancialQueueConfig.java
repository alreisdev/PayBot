package com.agile.paybot.financial.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FinancialQueueConfig {

    // Exchange for all financial saga events
    public static final String FINANCIAL_EXCHANGE = "financial.exchange";

    // Command queue (AI service → Financial service)
    public static final String PAYMENT_COMMAND_QUEUE = "financial.payment.command";
    public static final String PAYMENT_COMMAND_KEY = "payment.command";

    // Result queue (Financial service → AI service)
    public static final String PAYMENT_RESULT_QUEUE = "financial.payment.result";
    public static final String PAYMENT_RESULT_KEY = "payment.result";

    // Schedule command queue (AI service → Financial service)
    public static final String SCHEDULE_COMMAND_QUEUE = "financial.schedule.command";
    public static final String SCHEDULE_COMMAND_KEY = "schedule.command";

    // Schedule result queue (Financial service → AI service)
    public static final String SCHEDULE_RESULT_QUEUE = "financial.schedule.result";
    public static final String SCHEDULE_RESULT_KEY = "schedule.result";

    @Bean
    DirectExchange financialExchange() {
        return new DirectExchange(FINANCIAL_EXCHANGE);
    }

    @Bean
    Queue paymentCommandQueue() {
        return QueueBuilder.durable(PAYMENT_COMMAND_QUEUE).build();
    }

    @Bean
    Queue paymentResultQueue() {
        return QueueBuilder.durable(PAYMENT_RESULT_QUEUE).build();
    }

    @Bean
    Binding paymentCommandBinding(Queue paymentCommandQueue, DirectExchange financialExchange) {
        return BindingBuilder
                .bind(paymentCommandQueue)
                .to(financialExchange)
                .with(PAYMENT_COMMAND_KEY);
    }

    @Bean
    Binding paymentResultBinding(Queue paymentResultQueue, DirectExchange financialExchange) {
        return BindingBuilder
                .bind(paymentResultQueue)
                .to(financialExchange)
                .with(PAYMENT_RESULT_KEY);
    }

    @Bean
    Queue scheduleCommandQueue() {
        return QueueBuilder.durable(SCHEDULE_COMMAND_QUEUE).build();
    }

    @Bean
    Queue scheduleResultQueue() {
        return QueueBuilder.durable(SCHEDULE_RESULT_QUEUE).build();
    }

    @Bean
    Binding scheduleCommandBinding(Queue scheduleCommandQueue, DirectExchange financialExchange) {
        return BindingBuilder
                .bind(scheduleCommandQueue)
                .to(financialExchange)
                .with(SCHEDULE_COMMAND_KEY);
    }

    @Bean
    Binding scheduleResultBinding(Queue scheduleResultQueue, DirectExchange financialExchange) {
        return BindingBuilder
                .bind(scheduleResultQueue)
                .to(financialExchange)
                .with(SCHEDULE_RESULT_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        rabbitTemplate.setObservationEnabled(true);
        return rabbitTemplate;
    }
}

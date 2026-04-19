package com.orderprocessing.notificationservice.configuration

import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.InventoryReserved
import com.orderprocessing.shared.events.OrderFailed

import com.orderprocessing.shared.events.PaymentProcessed

import com.orderprocessing.shared.serialization.EventDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import tools.jackson.core.type.TypeReference

@Configuration
class KafkaConfig (
    @param:Value($$"${kafka.bootstrap-servers}") private val bootstrapServers: String,
) {
    @Bean
    fun paymentProcessedConsumerFactory(): ConsumerFactory<String, EventEnvelope<PaymentProcessed>> {
        val config =
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "notification-service",
            )

        return DefaultKafkaConsumerFactory(
            config,
            StringDeserializer(),
            EventDeserializer(object : TypeReference<EventEnvelope<PaymentProcessed>>() {}),
        )
    }

    @Bean
    fun orderFailedConsumerFactory(): ConsumerFactory<String, EventEnvelope<OrderFailed>> {
        val config =
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "notification-service",
            )
        return DefaultKafkaConsumerFactory(
            config,
            StringDeserializer(),
            EventDeserializer(object : TypeReference<EventEnvelope<OrderFailed>>() {}),
        )
    }

    @Bean
    fun orderFailedKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<OrderFailed>> =
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<OrderFailed>>().apply {
            setConsumerFactory(orderFailedConsumerFactory())
        }

    @Bean
    fun paymentProcessedKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentProcessed>> =
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentProcessed>>().apply {
            setConsumerFactory(paymentProcessedConsumerFactory())
        }
}
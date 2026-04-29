package com.orderprocessing.notifications.configuration

import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderFailed
import com.orderprocessing.shared.events.PaymentProcessed
import com.orderprocessing.shared.serialization.EventDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import tools.jackson.core.type.TypeReference

@Configuration
class KafkaConfig(
    @param:Value($$"${kafka.bootstrap-servers}") private val bootstrapServers: String,
    private val notificationProperties: NotificationProperties,
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
            setCommonErrorHandler(errorHandler(kafkaTemplate()))
        }

    @Bean
    fun paymentProcessedKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentProcessed>> =
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentProcessed>>().apply {
            setConsumerFactory(paymentProcessedConsumerFactory())
            setCommonErrorHandler(errorHandler(kafkaTemplate()))
        }

    @Bean
    fun errorHandler(kafkaTemplate: KafkaTemplate<String, String>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate)
        val backOff =
            FixedBackOff(
                notificationProperties.errorHandler.backOffIntervalMs,
                notificationProperties.errorHandler.backOffMaxAttempts,
            )
        return DefaultErrorHandler(recoverer, backOff)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> {
        val config =
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            )
        return KafkaTemplate(DefaultKafkaProducerFactory(config))
    }
}

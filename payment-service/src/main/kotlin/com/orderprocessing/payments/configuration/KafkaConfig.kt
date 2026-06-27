package com.orderprocessing.payments.configuration

import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.events.PaymentRetry
import com.orderprocessing.shared.serialization.EventDeserializer
import com.orderprocessing.shared.serialization.EventSerializer
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
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
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import tools.jackson.core.type.TypeReference

@Configuration
class KafkaConfig(
    @param:Value($$"${kafka.bootstrap-servers}") private val bootstrapServers: String,
    @param:Value($$"${kafka.security.protocol:PLAINTEXT}") private val securityProtocol: String,
    @param:Value($$"${kafka.properties.sasl.mechanism:}") private val saslMechanism: String,
    @param:Value($$"${kafka.properties.sasl.jaas.config:}") private val saslJaasConfig: String,
    private val paymentProperties: PaymentProperties,
) {
    private fun securityProps(): Map<String, Any> =
        buildMap {
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol)
            if (saslMechanism.isNotBlank()) put(SaslConfigs.SASL_MECHANISM, saslMechanism)
            if (saslJaasConfig.isNotBlank()) put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig)
        }

    @Bean
    fun producerFactory(): ProducerFactory<String, EventEnvelope<*>> {
        val config =
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to EventSerializer::class.java,
            ) + securityProps()

        return DefaultKafkaProducerFactory(config)
    }

    @Bean
    fun orderPlacedConsumerFactory(): ConsumerFactory<String, EventEnvelope<OrderPlaced>> {
        val config =
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "payment-service",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
            ) + securityProps()

        return DefaultKafkaConsumerFactory(
            config,
            StringDeserializer(),
            EventDeserializer(object : TypeReference<EventEnvelope<OrderPlaced>>() {}),
        )
    }

    @Bean
    fun retryConsumerFactory(): ConsumerFactory<String, EventEnvelope<PaymentRetry>> {
        val config =
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "payment-service",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
            ) + securityProps()

        return DefaultKafkaConsumerFactory(
            config,
            StringDeserializer(),
            EventDeserializer(object : TypeReference<EventEnvelope<PaymentRetry>>() {}),
        )
    }

    @Bean
    fun retryKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentRetry>> =
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentRetry>>().apply {
            setConsumerFactory(retryConsumerFactory())
            setCommonErrorHandler(errorHandler(kafkaTemplate()))
        }

    @Bean
    fun orderPlacedKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<OrderPlaced>> =
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<OrderPlaced>>().apply {
            setConsumerFactory(orderPlacedConsumerFactory())
            setCommonErrorHandler(errorHandler(kafkaTemplate()))
        }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> {
        val config =
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ) + securityProps()

        return KafkaTemplate(DefaultKafkaProducerFactory(config))
    }

    @Bean
    fun errorHandler(kafkaTemplate: KafkaTemplate<String, String>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate)
        val backOff =
            FixedBackOff(
                paymentProperties.errorHandler.backOffIntervalMs,
                paymentProperties.errorHandler.backOffMaxAttempts,
            )
        return DefaultErrorHandler(recoverer, backOff)
    }
}
package com.orderprocessing.analytics.configuration

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate

@TestConfiguration
class TestKafkaConfig(
    @param:Value($$"${kafka.bootstrap-servers}") private val bootstrapServers: String,
) {
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

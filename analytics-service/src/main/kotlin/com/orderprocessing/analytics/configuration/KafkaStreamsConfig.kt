package com.orderprocessing.analytics.configuration

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration
import org.springframework.kafka.config.KafkaStreamsConfiguration

@Configuration
class KafkaStreamsConfig(
    @param:Value($$"${kafka.bootstrap-servers}") private val bootstrapServers: String,
    @param:Value($$"${kafka.security.protocol:PLAINTEXT}") private val securityProtocol: String,
    @param:Value($$"${kafka.properties.sasl.mechanism:}") private val saslMechanism: String,
    @param:Value($$"${kafka.properties.sasl.jaas.config:}") private val saslJaasConfig: String,
    private val analyticsProperties: AnalyticsProperties,
) {
    @Bean(name = [KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME])
    fun streamsConfig(): KafkaStreamsConfiguration {
        val props =
            buildMap {
                put(StreamsConfig.APPLICATION_ID_CONFIG, analyticsProperties.kafka.applicationId)
                put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
                put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String()::class.java)
                put(StreamsConfig.STATE_DIR_CONFIG, analyticsProperties.kafka.stateDir)
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol)
                if (saslMechanism.isNotBlank()) put(SaslConfigs.SASL_MECHANISM, saslMechanism)
                if (saslJaasConfig.isNotBlank()) put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig)
            }

        return KafkaStreamsConfiguration(props)
    }
}
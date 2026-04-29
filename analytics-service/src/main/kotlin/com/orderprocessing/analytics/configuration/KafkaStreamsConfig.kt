package com.orderprocessing.analytics.configuration

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
    private val analyticsProperties: AnalyticsProperties,
) {
    @Bean(name = [KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME])
    fun streamsConfig(): KafkaStreamsConfiguration {
        val props =
            mapOf(
                StreamsConfig.APPLICATION_ID_CONFIG to analyticsProperties.kafka.applicationId,
                StreamsConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG to Serdes.String()::class.java,
                StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG to Serdes.String()::class.java,
                StreamsConfig.STATE_DIR_CONFIG to analyticsProperties.kafka.stateDir,
            )
        return KafkaStreamsConfiguration(props)
    }
}

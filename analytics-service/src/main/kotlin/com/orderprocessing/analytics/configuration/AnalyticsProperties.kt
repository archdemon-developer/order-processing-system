package com.orderprocessing.analytics.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "analytics")
data class AnalyticsProperties(
    val kafka: KafkaProperties,
) {
    data class KafkaProperties(
        val applicationId: String,
        val stateDir: String,
    )
}

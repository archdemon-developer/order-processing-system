package com.orderprocessing.notifications.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "notification")
data class NotificationProperties(
    val errorHandler: ErrorHandlerProperties,
) {
    data class ErrorHandlerProperties(
        val backOffIntervalMs: Long,
        val backOffMaxAttempts: Long,
    )
}

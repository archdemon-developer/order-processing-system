package com.orderprocessing.paymentservice.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payment")
data class PaymentProperties(
    val failureRate: Double,
    val retry: RetryProperties,
    val errorHandler: ErrorHandlerProperties,
) {
    data class RetryProperties(
        val maxAttempts: Int,
        val delayMs: Long,
    )

    data class ErrorHandlerProperties(
        val backOffIntervalMs: Long,
        val backOffMaxAttempts: Long,
    )
}

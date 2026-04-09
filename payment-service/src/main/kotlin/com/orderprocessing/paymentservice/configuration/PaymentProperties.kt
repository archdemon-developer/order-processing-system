package com.orderprocessing.paymentservice.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payment")
data class PaymentProperties(
    val failureRate: Double,
    val retry: RetryProperties,
) {
    data class RetryProperties(
        val maxAttempts: Int,
        val delayMs: Long,
    )
}

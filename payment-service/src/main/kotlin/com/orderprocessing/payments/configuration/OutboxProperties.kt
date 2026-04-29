package com.orderprocessing.payments.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "outbox")
data class OutboxProperties(
    val retentionHours: Long,
    val cleanupIntervalMs: Long,
)

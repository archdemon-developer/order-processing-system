package com.orderprocessing.paymentservice.job

import com.orderprocessing.paymentservice.configuration.OutboxProperties
import com.orderprocessing.shared.outbox.OutboxEventRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class OutboxCleanupJob(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxProperties: OutboxProperties,
) {
    @Scheduled(fixedDelayString = "\${outbox.cleanup-interval-ms}")
    fun cleanup() {
        val cutoff = Instant.now().minus(outboxProperties.retentionHours, ChronoUnit.HOURS)
        outboxEventRepository.deleteByCreatedatBefore(cutoff)
    }
}

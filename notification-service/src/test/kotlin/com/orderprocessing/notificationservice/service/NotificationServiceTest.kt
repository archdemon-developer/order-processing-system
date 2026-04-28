package com.orderprocessing.notificationservice.service

import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderFailed
import com.orderprocessing.shared.events.PaymentProcessed
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class NotificationServiceTest {
    private val notificationService = NotificationService()

    @Test
    fun `notifyOrderSuccess - completes without exception`() {
        val envelope =
            EventEnvelope(
                eventId = UUID.randomUUID(),
                eventType = "payment-processed",
                occurredAt = Instant.now(),
                payload =
                    PaymentProcessed(
                        orderId = UUID.randomUUID(),
                        transactionId = UUID.randomUUID(),
                        customerId = UUID.randomUUID(),
                    ),
            )
        notificationService.notifyOrderSuccess(envelope)
    }

    @Test
    fun `notifyOrderFailure - completes without exception`() {
        val envelope =
            EventEnvelope(
                eventId = UUID.randomUUID(),
                eventType = "order-failed",
                occurredAt = Instant.now(),
                payload =
                    OrderFailed(
                        orderId = UUID.randomUUID(),
                        customerId = UUID.randomUUID(),
                        reason = "Order processing failed",
                    ),
            )
        notificationService.notifyOrderFailure(envelope)
    }
}

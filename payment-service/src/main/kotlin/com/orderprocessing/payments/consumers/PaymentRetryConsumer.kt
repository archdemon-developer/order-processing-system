package com.orderprocessing.payments.consumers

import com.orderprocessing.payments.service.PaymentService
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.PaymentRetry
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentRetryConsumer(
    private val paymentService: PaymentService,
) {
    @KafkaListener(topics = ["payment-retry"], groupId = "payment-service", containerFactory = "retryKafkaListenerContainerFactory")
    fun paymentRetryConsumer(envelope: EventEnvelope<PaymentRetry>) {
        paymentService.processRetry(envelope)
    }
}

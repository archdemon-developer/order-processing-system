package com.orderprocessing.notificationservice.consumers

import com.orderprocessing.notificationservice.service.NotificationService
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.PaymentProcessed
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentProcessedConsumer(
    private val notificationService: NotificationService,
) {
    @KafkaListener(
        topics = ["payment-processed"],
        groupId = "notification-service",
        containerFactory = "paymentProcessedKafkaListenerContainerFactory",
    )
    fun paymentProcessedConsumer(envelope: EventEnvelope<PaymentProcessed>) {
        notificationService.notifyOrderSuccess(envelope)
    }
}

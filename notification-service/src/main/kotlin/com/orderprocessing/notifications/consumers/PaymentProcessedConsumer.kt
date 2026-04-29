package com.orderprocessing.notifications.consumers

import com.orderprocessing.notifications.service.NotificationService
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

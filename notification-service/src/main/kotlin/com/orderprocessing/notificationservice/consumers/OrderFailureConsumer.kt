package com.orderprocessing.notificationservice.consumers

import com.orderprocessing.notificationservice.service.NotificationService
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderFailed
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OrderFailureConsumer(
    private val notificationService: NotificationService,
) {
    @KafkaListener(
        topics = ["order-failed"],
        groupId = "notification-service",
        containerFactory = "orderFailedKafkaListenerContainerFactory",
    )
    fun orderFailureConsumer(envelope: EventEnvelope<OrderFailed>) {
        notificationService.notifyOrderFailure(envelope)
    }
}

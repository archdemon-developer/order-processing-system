package com.orderprocessing.payments.consumers

import com.orderprocessing.payments.service.PaymentService
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderPlaced
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OrderPlacedConsumer(
    private val paymentService: PaymentService,
) {
    @KafkaListener(topics = ["order-placed"], groupId = "payment-service", containerFactory = "orderPlacedKafkaListenerContainerFactory")
    fun orderPlacedConsumer(envelope: EventEnvelope<OrderPlaced>) {
        paymentService.processPayment(envelope)
    }
}

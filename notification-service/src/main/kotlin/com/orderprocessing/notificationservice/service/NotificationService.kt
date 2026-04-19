package com.orderprocessing.notificationservice.service

import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.InventoryReserved
import com.orderprocessing.shared.events.OrderFailed
import com.orderprocessing.shared.events.PaymentProcessed
import org.springframework.stereotype.Service

@Service
class NotificationService {

    fun notifyOrderFailure(envelope: EventEnvelope<OrderFailed>) {
        println("Your order could not be processed. orderId=${envelope.payload.orderId}")
    }

    fun notifyOrderSuccess(envelope: EventEnvelope<PaymentProcessed>) {
        println("Your payment was successful. orderId=${envelope.payload.orderId}")
    }

    fun notifyInventoryReserved(envelope: EventEnvelope<InventoryReserved>) {
        println("Your order is confirmed. orderId=${envelope.payload.orderId}")
    }
}
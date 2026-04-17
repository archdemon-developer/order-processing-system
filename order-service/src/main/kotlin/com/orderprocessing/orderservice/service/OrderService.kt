package com.orderprocessing.orderservice.service

import com.orderprocessing.orderservice.entities.Order
import com.orderprocessing.orderservice.models.request.CreateOrderRequest
import com.orderprocessing.orderservice.models.response.CreateOrderResponse
import com.orderprocessing.orderservice.repositories.OrderRepository
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.model.OrderItem
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ExecutionException

@Service
@Transactional
class OrderService(
    private val orderRepository: OrderRepository,
    private val kafkaTemplate: KafkaTemplate<String, EventEnvelope<OrderPlaced>>,
) {
    fun createOrder(request: CreateOrderRequest): CreateOrderResponse {
        val order = orderRepository.save(buildOrder(request))
        publishOrderPlaced(order)
        return order.toResponse()
    }

    private fun buildOrder(request: CreateOrderRequest): Order =
        Order().apply {
            customerId = request.customerId
            items = request.items.map { OrderItem(it.productId, it.quantity, it.pricePerItem) }
            totalPrice = items.sumOf { it.pricePerItem.multiply(it.quantity.toBigDecimal()) }
        }

    private fun publishOrderPlaced(order: Order) {
        val event =
            EventEnvelope(
                eventId = UUID.randomUUID(),
                eventType = "order-placed",
                occurredAt = Instant.now(),
                payload =
                    OrderPlaced(
                        orderId = order.id,
                        customerId = order.customerId,
                        items = order.items,
                        totalPrice = order.totalPrice,
                    ),
            )
        try {
            kafkaTemplate.send("order-placed", order.id.toString(), event).get()
        } catch (e: ExecutionException) {
            throw RuntimeException("Failed to publish order event", e.cause)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("Interrupted while publishing order event", e)
        }
    }

    private fun Order.toResponse() =
        CreateOrderResponse(
            orderId = id,
            status = status.name,
            totalPrice = totalPrice,
            createdAt = createdAt,
        )
}

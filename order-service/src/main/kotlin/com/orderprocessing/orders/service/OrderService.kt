package com.orderprocessing.orders.service

import com.orderprocessing.orders.entities.Order
import com.orderprocessing.orders.models.request.CreateOrderRequest
import com.orderprocessing.orders.models.response.CreateOrderResponse
import com.orderprocessing.orders.repositories.OrderRepository
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.model.OrderItem
import com.orderprocessing.shared.outbox.OutboxEvent
import com.orderprocessing.shared.outbox.OutboxEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class OrderService(
    private val orderRepository: OrderRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: JsonMapper,
) {
    fun createOrder(request: CreateOrderRequest): CreateOrderResponse {
        val order = orderRepository.save(buildOrder(request))
        outboxEventRepository.save(buildOutboxEvent(order))
        return order.toResponse()
    }

    private fun buildOrder(request: CreateOrderRequest): Order =
        Order().apply {
            customerId = request.customerId
            items = request.items.map { OrderItem(it.productId, it.quantity, it.pricePerItem) }
            totalPrice = items.sumOf { it.pricePerItem.multiply(it.quantity.toBigDecimal()) }
        }

    private fun buildOutboxEvent(order: Order): OutboxEvent =
        OutboxEvent().apply {
            aggregatetype = "order-placed"
            aggregateid = order.id.toString()
            type = "OrderPlaced"
            payload =
                objectMapper.writeValueAsString(
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
                    ),
                )
        }

    private fun Order.toResponse() =
        CreateOrderResponse(
            orderId = id,
            status = status.name,
            totalPrice = totalPrice,
            createdAt = createdAt,
        )
}

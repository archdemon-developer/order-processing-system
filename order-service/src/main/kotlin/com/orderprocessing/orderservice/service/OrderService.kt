package com.orderprocessing.orderservice.service

import com.orderprocessing.orderservice.entities.Order
import com.orderprocessing.orderservice.enums.OrderStatus
import com.orderprocessing.orderservice.models.request.CreateOrderRequest
import com.orderprocessing.orderservice.models.response.CreateOrderResponse
import com.orderprocessing.orderservice.repositories.OrderRepository
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.model.OrderItem
import jakarta.transaction.Transactional
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class OrderService(
    private val orderRepository: OrderRepository,
    private val kafkaTemplate: KafkaTemplate<String, EventEnvelope<OrderPlaced>>) {

    fun createOrder(createOrderRequest: CreateOrderRequest): CreateOrderResponse {
        val totalPrice = createOrderRequest.items.sumOf { orderItem -> orderItem.pricePerItem.multiply(orderItem.quantity.toBigDecimal()) }
        val orderItems = createOrderRequest.items.map { itemRequest -> OrderItem(productId = itemRequest.productId, quantity = itemRequest.quantity, pricePerItem = itemRequest.pricePerItem) }
        val order = buildOrder(createOrderRequest, totalPrice, orderItems)
        val savedOrder = orderRepository.save(order)
        val orderPlaced = OrderPlaced(orderId = savedOrder.id, customerId = savedOrder.customerId, items = savedOrder.items, totalPrice = savedOrder.totalPrice)
        val eventEnvelope = EventEnvelope<OrderPlaced>(eventId = UUID.randomUUID(), eventType = "order-placed", payload = orderPlaced, occurredAt = Instant.now())
        kafkaTemplate.send("order-placed", savedOrder.id.toString(), eventEnvelope).get()
        return CreateOrderResponse(orderId = savedOrder.id, status = savedOrder.status.toString(), totalPrice = savedOrder.totalPrice, createdAt = savedOrder.createdAt)
    }

    private fun buildOrder(createOrderRequest: CreateOrderRequest, totalPrice: BigDecimal, orderItems: List<OrderItem>): Order {
        val order = Order()
        order.customerId = createOrderRequest.customerId
        order.totalPrice = totalPrice
        order.items = orderItems
        return order
    }
}
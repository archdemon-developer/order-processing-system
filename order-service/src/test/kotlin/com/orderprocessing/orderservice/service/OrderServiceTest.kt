package com.orderprocessing.orderservice.service

import com.orderprocessing.orderservice.entities.Order
import com.orderprocessing.orderservice.exception.GlobalExceptionHandler
import com.orderprocessing.orderservice.models.request.CreateOrderRequest
import com.orderprocessing.orderservice.models.request.OrderItemRequest
import com.orderprocessing.orderservice.repositories.OrderRepository
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.model.OrderItem
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture

@ExtendWith(MockKExtension::class)
class OrderServiceTest {
    @MockK lateinit var orderRepository: OrderRepository

    @MockK lateinit var kafkaTemplate: KafkaTemplate<String, EventEnvelope<OrderPlaced>>

    private lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        orderService = OrderService(orderRepository, kafkaTemplate)
    }

    @Test
    fun `createOrder - happy path - saves order and publishes event`() {
        val request =
            CreateOrderRequest(
                customerId = UUID.randomUUID(),
                items =
                    listOf(
                        OrderItemRequest(productId = UUID.randomUUID(), quantity = 2, pricePerItem = BigDecimal("10.00")),
                    ),
            )
        val savedOrder =
            Order().apply {
                customerId = request.customerId
                totalPrice = BigDecimal("20.00")
                items = listOf(OrderItem(request.items[0].productId, 2, BigDecimal("10.00")))
            }
        val future = CompletableFuture.completedFuture(mockk<SendResult<String, EventEnvelope<OrderPlaced>>>())

        every { orderRepository.save(any()) } returns savedOrder
        every { kafkaTemplate.send(any(), any(), any()) } returns future

        val response = orderService.createOrder(request)

        assertEquals(savedOrder.id, response.orderId)
        assertEquals("PENDING", response.status)
        assertEquals(BigDecimal("20.00"), response.totalPrice)
        verify(exactly = 1) { kafkaTemplate.send("order-placed", savedOrder.id.toString(), any()) }
    }

    @Test
    fun `createOrder - total price - calculated correctly across multiple items`() {
        val request =
            CreateOrderRequest(
                customerId = UUID.randomUUID(),
                items =
                    listOf(
                        OrderItemRequest(productId = UUID.randomUUID(), quantity = 3, pricePerItem = BigDecimal("5.00")),
                        OrderItemRequest(productId = UUID.randomUUID(), quantity = 2, pricePerItem = BigDecimal("12.50")),
                    ),
            )
        val expectedTotal = BigDecimal("40.00") // (3x5.00) + (2x12.50)
        val savedOrder =
            Order().apply {
                customerId = request.customerId
                totalPrice = expectedTotal
                items = emptyList()
            }
        val future = CompletableFuture.completedFuture(mockk<SendResult<String, EventEnvelope<OrderPlaced>>>())

        every { orderRepository.save(any()) } answers {
            // capture what was actually passed to save and verify total price
            val orderArg = firstArg<Order>()
            assertEquals(expectedTotal, orderArg.totalPrice)
            savedOrder
        }
        every { kafkaTemplate.send(any(), any(), any()) } returns future

        orderService.createOrder(request)
    }

    @Test
    fun `createOrder - kafka failure - exception propagates`() {
        val request =
            CreateOrderRequest(
                customerId = UUID.randomUUID(),
                items =
                    listOf(
                        OrderItemRequest(productId = UUID.randomUUID(), quantity = 1, pricePerItem = BigDecimal("9.99")),
                    ),
            )
        val savedOrder =
            Order().apply {
                customerId = request.customerId
                totalPrice = BigDecimal("9.99")
                items = emptyList()
            }
        val future =
            CompletableFuture.failedFuture<SendResult<String, EventEnvelope<OrderPlaced>>>(
                RuntimeException("Kafka unavailable"),
            )

        every { orderRepository.save(any()) } returns savedOrder
        every { kafkaTemplate.send(any(), any(), any()) } returns future

        assertThrows<RuntimeException> {
            orderService.createOrder(request)
        }
    }

    @Test
    fun `handleException - null message - uses fallback message`() {
        val handler = GlobalExceptionHandler()
        val ex =
            object : Exception() {
                override val message: String? = null
            }
        val response = handler.handleException(ex)
        assertEquals("An unexpected error occurred", response.body?.message)
    }
}

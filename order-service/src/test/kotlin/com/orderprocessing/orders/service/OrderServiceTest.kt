package com.orderprocessing.orders.service

import com.orderprocessing.orders.entities.Order
import com.orderprocessing.orders.exception.GlobalExceptionHandler
import com.orderprocessing.orders.models.request.CreateOrderRequest
import com.orderprocessing.orders.models.request.OrderItemRequest
import com.orderprocessing.orders.repositories.OrderRepository
import com.orderprocessing.shared.model.OrderItem
import com.orderprocessing.shared.outbox.OutboxEventRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.UUID

@Tag("unit")
@ExtendWith(MockKExtension::class)
class OrderServiceTest {
    @MockK lateinit var orderRepository: OrderRepository

    @MockK lateinit var outboxEventRepository: OutboxEventRepository

    @MockK lateinit var objectMapper: JsonMapper

    private lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        orderService = OrderService(orderRepository, outboxEventRepository, objectMapper)
    }

    @Test
    fun `createOrder - happy path - saves order and publishes outbox event`() {
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

        every { orderRepository.save(any()) } returns savedOrder
        every { outboxEventRepository.save(any()) } returns mockk()
        every { objectMapper.writeValueAsString(any()) } returns "{}"

        val response = orderService.createOrder(request)

        assertEquals(savedOrder.id, response.orderId)
        assertEquals("PENDING", response.status)
        assertEquals(BigDecimal("20.00"), response.totalPrice)
        verify(exactly = 1) { orderRepository.save(any()) }
        verify(exactly = 1) { outboxEventRepository.save(any()) }
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
        val expectedTotal = BigDecimal("40.00")
        val savedOrder =
            Order().apply {
                customerId = request.customerId
                totalPrice = expectedTotal
                items = emptyList()
            }

        every { orderRepository.save(any()) } answers {
            val orderArg = firstArg<Order>()
            assertEquals(expectedTotal, orderArg.totalPrice)
            savedOrder
        }
        every { outboxEventRepository.save(any()) } returns mockk()
        every { objectMapper.writeValueAsString(any()) } returns "{}"

        orderService.createOrder(request)
    }

    @Test
    fun `createOrder - outbox save failure - exception propagates`() {
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

        every { orderRepository.save(any()) } returns savedOrder
        every { objectMapper.writeValueAsString(any()) } returns "{}"
        every { outboxEventRepository.save(any()) } throws RuntimeException("DB unavailable")

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

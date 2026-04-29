package com.orderprocessing.orders.integration

import com.ninjasquad.springmockk.MockkSpyBean
import com.orderprocessing.orders.models.error.ErrorResponse
import com.orderprocessing.orders.models.request.CreateOrderRequest
import com.orderprocessing.orders.models.request.OrderItemRequest
import com.orderprocessing.orders.models.response.CreateOrderResponse
import com.orderprocessing.orders.repositories.OrderRepository
import com.orderprocessing.orders.service.OrderService
import com.orderprocessing.shared.outbox.OutboxEventRepository
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.util.UUID

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
@ActiveProfiles("test")
class IntegrationTests {
    companion object {
        @JvmStatic
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        @JvmStatic
        @Container
        val kafka = KafkaContainer("apache/kafka-native:3.8.0")

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("kafka.bootstrap-servers") { kafka.bootstrapServers }
        }
    }

    @Autowired lateinit var restTestClient: RestTestClient

    @Autowired lateinit var orderRepository: OrderRepository

    @Autowired lateinit var outboxEventRepository: OutboxEventRepository

    @MockkSpyBean lateinit var orderService: OrderService

    @BeforeEach
    fun cleanUp() {
        orderRepository.deleteAll()
        outboxEventRepository.deleteAll()
    }

    @Test
    fun `POST orders - happy path - returns 201, persists order and outbox event`() {
        val request =
            CreateOrderRequest(
                customerId = UUID.randomUUID(),
                items =
                    listOf(
                        OrderItemRequest(productId = UUID.randomUUID(), quantity = 2, pricePerItem = BigDecimal("15.00")),
                    ),
            )

        val response =
            restTestClient
                .post()
                .uri("/api/v1/orders")
                .body(request)
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody(CreateOrderResponse::class.java)
                .returnResult()
                .responseBody!!

        assertEquals("PENDING", response.status)
        assertEquals(BigDecimal("30.00"), response.totalPrice)

        val savedOrder = orderRepository.findById(response.orderId)
        assertTrue(savedOrder.isPresent)
        assertEquals(request.customerId, savedOrder.get().customerId)

        val outboxEvents = outboxEventRepository.findAll()
        assertEquals(1, outboxEvents.size)
        assertEquals("order-placed", outboxEvents[0].aggregatetype)
        assertEquals(response.orderId.toString(), outboxEvents[0].aggregateid)
        assertEquals("OrderPlaced", outboxEvents[0].type)
    }

    @Test
    fun `POST orders - invalid request - returns 400`() {
        val request =
            CreateOrderRequest(
                customerId = UUID.randomUUID(),
                items = emptyList(),
            )

        val errorResponse =
            restTestClient
                .post()
                .uri("/api/v1/orders")
                .body(request)
                .exchange()
                .expectStatus()
                .isBadRequest
                .expectBody(ErrorResponse::class.java)
                .returnResult()
                .responseBody!!

        assertEquals(400, errorResponse.status)
    }

    @Test
    fun `POST orders - outbox save failure - order not persisted`() {
        val request =
            CreateOrderRequest(
                customerId = UUID.randomUUID(),
                items =
                    listOf(
                        OrderItemRequest(productId = UUID.randomUUID(), quantity = 1, pricePerItem = BigDecimal("9.99")),
                    ),
            )

        every { orderService.createOrder(any()) } throws RuntimeException("DB unavailable")

        restTestClient
            .post()
            .uri("/api/v1/orders")
            .body(request)
            .exchange()
            .expectStatus()
            .is5xxServerError

        assertEquals(0L, orderRepository.count())
    }
}

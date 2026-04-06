package com.orderprocessing.orderservice.integration

import com.ninjasquad.springmockk.MockkSpyBean
import com.orderprocessing.orderservice.models.error.ErrorResponse
import com.orderprocessing.orderservice.models.request.CreateOrderRequest
import com.orderprocessing.orderservice.models.request.OrderItemRequest
import com.orderprocessing.orderservice.models.response.CreateOrderResponse
import com.orderprocessing.orderservice.repositories.OrderRepository
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderPlaced
import io.mockk.every
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture

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

    @MockkSpyBean lateinit var kafkaTemplate: KafkaTemplate<String, EventEnvelope<OrderPlaced>>

    @BeforeEach
    fun cleanUp() {
        orderRepository.deleteAll()
    }

    @Test
    fun `POST orders - happy path - returns 201, persists order, publishes event`() {
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

        val saved = orderRepository.findById(response.orderId)
        assertTrue(saved.isPresent)
        assertEquals(request.customerId, saved.get().customerId)

        createKafkaConsumer().use { consumer ->
            consumer.subscribe(listOf("order-placed"))
            val records = consumer.poll(Duration.ofSeconds(10))
            assertFalse(records.isEmpty)
            val value = records.first().value()
            assertTrue(value.contains(response.orderId.toString()))
            assertTrue(value.contains("order-placed"))
        }
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
    fun `POST orders - kafka failure - order not persisted`() {
        val request =
            CreateOrderRequest(
                customerId = UUID.randomUUID(),
                items =
                    listOf(
                        OrderItemRequest(productId = UUID.randomUUID(), quantity = 1, pricePerItem = BigDecimal("9.99")),
                    ),
            )
        val failed =
            CompletableFuture.failedFuture<SendResult<String, EventEnvelope<OrderPlaced>>>(
                RuntimeException("Kafka unavailable"),
            )
        every { kafkaTemplate.send(any<String>(), any<String>(), any()) } returns failed

        restTestClient
            .post()
            .uri("/api/v1/orders")
            .body(request)
            .exchange()
            .expectStatus()
            .is5xxServerError

        assertEquals(0L, orderRepository.count())
    }

    private fun createKafkaConsumer(): KafkaConsumer<String, String> =
        KafkaConsumer(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "test-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ),
        )
}

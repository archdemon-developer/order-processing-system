package com.orderprocessing.payments.integration

import com.orderprocessing.payments.configuration.TestKafkaConfig
import com.orderprocessing.payments.repositories.PaymentRepository
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.model.OrderItem
import com.orderprocessing.shared.outbox.OutboxEventRepository
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Tag("integration")
@Testcontainers
@Import(TestKafkaConfig::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DirtiesContext
class PaymentSuccessIntegrationTests {
    companion object {
        private const val KAFKA_LISTENER_STARTUP_WAIT_MS = 2000L

        @JvmStatic
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        @JvmStatic
        @Container
        val kafka = KafkaContainer("apache/kafka-native:3.8.0")

        @JvmStatic
        @Container
        val redis = RedisContainer("redis:latest")

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("kafka.bootstrap-servers") { kafka.bootstrapServers }
            registry.add("payment.failure-rate") { 0.0 }
            registry.add("payment.retry.max-attempts") { 3 }
            registry.add("payment.retry.delay-ms") { 500 }
            registry.add("payment.error-handler.back-off-interval-ms") { 1000 }
            registry.add("payment.error-handler.back-off-max-attempts") { 3 }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.firstMappedPort }
        }
    }

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, EventEnvelope<*>>

    @Autowired lateinit var redisTemplate: RedisTemplate<String, String>

    @Autowired lateinit var paymentRepository: PaymentRepository

    @Autowired lateinit var outboxEventRepository: OutboxEventRepository

    @Autowired lateinit var jsonMapper: JsonMapper

    @BeforeEach
    fun cleanUp() {
        paymentRepository.deleteAll()
        outboxEventRepository.deleteAll()
    }

    @Test
    fun `payment flow happy path - message successfully processed, db record saved and outbox event written`() {
        val orderPlaced =
            OrderPlaced(
                orderId = UUID.randomUUID(),
                customerId = UUID.randomUUID(),
                items = listOf(OrderItem(productId = UUID.randomUUID(), quantity = 2, pricePerItem = BigDecimal("10.00"))),
                totalPrice = BigDecimal("20.00"),
            )

        Thread.sleep(KAFKA_LISTENER_STARTUP_WAIT_MS.milliseconds.toJavaDuration())
        kafkaTemplate
            .send(
                "order-placed",
                orderPlaced.orderId.toString(),
                EventEnvelope(
                    eventId = UUID.randomUUID(),
                    eventType = "order-placed",
                    occurredAt = Instant.now(),
                    payload = orderPlaced,
                ),
            ).get()

        Thread.sleep(3000.milliseconds.toJavaDuration())

        val saved = paymentRepository.findByOrderId(orderPlaced.orderId)
        assertNotNull(saved)
        assertEquals(orderPlaced.customerId, saved!!.customerId)
        assertTrue(redisTemplate.hasKey("idempotency:payment:${orderPlaced.orderId}"))

        val outboxEvents = outboxEventRepository.findAll()
        assertEquals(1, outboxEvents.size)
        assertEquals("payment-processed", outboxEvents[0].aggregatetype)
        assertEquals(orderPlaced.orderId.toString(), outboxEvents[0].aggregateid)
    }
}

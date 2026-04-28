package com.orderprocessing.paymentservice.integration

import com.orderprocessing.paymentservice.configuration.TestKafkaConfig
import com.orderprocessing.paymentservice.entities.Payment
import com.orderprocessing.paymentservice.enums.PaymentStatus
import com.orderprocessing.paymentservice.repositories.PaymentRepository
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.events.PaymentRetry
import com.orderprocessing.shared.model.OrderItem
import com.orderprocessing.shared.outbox.OutboxEventRepository
import com.redis.testcontainers.RedisContainer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@Tag("integration")
@Testcontainers
@Import(TestKafkaConfig::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DirtiesContext
class PaymentFailureIntegrationTests {
    companion object {
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
            registry.add("payment.failure-rate") { 1.0 }
            registry.add("payment.retry.max-attempts") { 3 }
            registry.add("payment.retry.delay-ms") { 0 }
            registry.add("payment.error-handler.back-off-interval-ms") { 1000 }
            registry.add("payment.error-handler.back-off-max-attempts") { 3 }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.firstMappedPort }
        }
    }

    @Autowired lateinit var testKafkaTemplate: KafkaTemplate<String, EventEnvelope<*>>

    @Autowired lateinit var redisTemplate: RedisTemplate<String, String>

    @Autowired lateinit var paymentRepository: PaymentRepository

    @Autowired lateinit var outboxEventRepository: OutboxEventRepository

    @BeforeEach
    fun cleanUp() {
        paymentRepository.deleteAll()
        outboxEventRepository.deleteAll()
    }

    @Test
    fun `order-placed with failed payment - saves retrying status and writes payment-retry outbox event`() {
        val orderPlaced =
            OrderPlaced(
                orderId = UUID.randomUUID(),
                customerId = UUID.randomUUID(),
                items = listOf(OrderItem(productId = UUID.randomUUID(), quantity = 2, pricePerItem = BigDecimal("10.00"))),
                totalPrice = BigDecimal("20.00"),
            )

        testKafkaTemplate.send(
            "order-placed",
            orderPlaced.orderId.toString(),
            EventEnvelope(
                eventId = UUID.randomUUID(),
                eventType = "order-placed",
                occurredAt = Instant.now(),
                payload = orderPlaced,
            ),
        )

        await().atMost(15, TimeUnit.SECONDS).untilAsserted {
            val saved = paymentRepository.findByOrderId(orderPlaced.orderId)
            assertNotNull(saved)
            assertEquals(PaymentStatus.RETRYING, saved!!.status)
        }

        val outboxEvents = outboxEventRepository.findAll()
        assertEquals(1, outboxEvents.size)
        assertEquals("payment-retry", outboxEvents[0].aggregatetype)
        assertEquals(orderPlaced.orderId.toString(), outboxEvents[0].aggregateid)
        assertFalse(redisTemplate.hasKey("idempotency:payment:${orderPlaced.orderId}"))
    }

    @Test
    fun `payment-retry exhausted - saves failed status and writes order-failed outbox event`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()

        val existingPayment =
            Payment().apply {
                this.orderId = orderId
                this.customerId = customerId
                this.status = PaymentStatus.RETRYING
                this.attempts = 1
            }
        paymentRepository.save(existingPayment)

        testKafkaTemplate.send(
            "payment-retry",
            orderId.toString(),
            EventEnvelope(
                eventId = UUID.randomUUID(),
                eventType = "payment-retry",
                occurredAt = Instant.now(),
                payload =
                    PaymentRetry(
                        orderId = orderId,
                        customerId = customerId,
                        items = listOf(OrderItem(productId = UUID.randomUUID(), quantity = 2, pricePerItem = BigDecimal("10.00"))),
                        totalPrice = BigDecimal("20.00"),
                        attempts = 3,
                    ),
            ),
        )

        await().atMost(15, TimeUnit.SECONDS).untilAsserted {
            val saved = paymentRepository.findByOrderId(orderId)
            assertNotNull(saved)
            assertEquals(PaymentStatus.FAILED, saved!!.status)
        }

        val outboxEvents = outboxEventRepository.findAll()
        assertEquals(1, outboxEvents.size)
        assertEquals("order-failed", outboxEvents[0].aggregatetype)
        assertEquals(orderId.toString(), outboxEvents[0].aggregateid)
    }
}

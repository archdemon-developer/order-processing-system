package com.orderprocessing.paymentservice.integration

import com.orderprocessing.paymentservice.enums.PaymentStatus
import com.orderprocessing.paymentservice.repositories.PaymentRepository
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.model.OrderItem
import com.redis.testcontainers.RedisContainer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
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
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Tag("integration")
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
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
            registry.add("payment.retry.delay-ms") { 500 }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.firstMappedPort }
        }
    }

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, EventEnvelope<*>>

    @Autowired lateinit var redisTemplate: RedisTemplate<String, String>

    @Autowired lateinit var paymentRepository: PaymentRepository

    @BeforeEach
    fun cleanUp() {
        paymentRepository.deleteAll()
    }

    @Test
    fun `payment flow failure path - message not processed, db record saved and kafka sends payment retry request, eventually fails`() {
        val orderPlaced =
            OrderPlaced(
                orderId = UUID.randomUUID(),
                customerId = UUID.randomUUID(),
                items =
                    listOf(
                        OrderItem(productId = UUID.randomUUID(), quantity = 2, pricePerItem = BigDecimal("10.00")),
                    ),
                totalPrice = BigDecimal("20.00"),
            )

        kafkaTemplate.send(
            "order-placed",
            orderPlaced.orderId.toString(),
            EventEnvelope(
                eventId = UUID.randomUUID(),
                eventType = "order-placed",
                occurredAt = Instant.now(),
                payload = orderPlaced,
            ),
        )

        // Wait for all retries to exhaust (3 messages on payment-retry)
        createKafkaConsumer().use { consumer ->
            consumer.subscribe(listOf("payment-retry"))
            var retryCount = 0
            val deadline = System.currentTimeMillis() + 15_000
            while (retryCount < 3 && System.currentTimeMillis() < deadline) {
                val records = consumer.poll(Duration.ofSeconds(3))
                retryCount += records.count()
            }
            assertEquals(3, retryCount)
        }

        createKafkaConsumer().use { consumer ->
            consumer.subscribe(listOf("order-failed"))
            val records = consumer.poll(Duration.ofSeconds(10))
            assertFalse(records.isEmpty)
            val value = records.first().value()
            assertTrue(value.contains(orderPlaced.orderId.toString()))
            assertTrue(value.contains("order-failed"))
        }

        val saved = paymentRepository.findByOrderId(orderPlaced.orderId)
        assertNotNull(saved)
        assertEquals(orderPlaced.customerId, saved.customerId)
        assertEquals(PaymentStatus.FAILED, saved.status)
        assertFalse(redisTemplate.hasKey("idempotency:payment:${orderPlaced.orderId}"))
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

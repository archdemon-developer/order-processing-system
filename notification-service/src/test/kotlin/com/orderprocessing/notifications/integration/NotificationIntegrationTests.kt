package com.orderprocessing.notifications.integration

import com.ninjasquad.springmockk.MockkSpyBean
import com.orderprocessing.notifications.configuration.TestKafkaConfig
import com.orderprocessing.notifications.service.NotificationService
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderFailed
import com.orderprocessing.shared.events.PaymentProcessed
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Tag("integration")
@Testcontainers
@Import(TestKafkaConfig::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DirtiesContext
class NotificationIntegrationTests {
    companion object {
        @JvmStatic
        @Container
        val kafka = KafkaContainer("apache/kafka-native:3.8.0")

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("kafka.bootstrap-servers") { kafka.bootstrapServers }
        }
    }

    @Autowired
    lateinit var testKafkaTemplate: KafkaTemplate<String, EventEnvelope<*>>

    @MockkSpyBean
    lateinit var notificationService: NotificationService

    @Test
    fun `payment processed - notifyOrderSuccess is invoked`() {
        val latch = CountDownLatch(1)
        val payload =
            PaymentProcessed(
                orderId = UUID.randomUUID(),
                transactionId = UUID.randomUUID(),
                customerId = UUID.randomUUID(),
                totalPrice = BigDecimal("15.00"),
            )
        val envelope =
            EventEnvelope(
                eventId = UUID.randomUUID(),
                eventType = "payment-processed",
                occurredAt = Instant.now(),
                payload = payload,
            )

        every { notificationService.notifyOrderSuccess(any()) } answers {
            callOriginal()
            latch.countDown()
        }

        testKafkaTemplate.send("payment-processed", payload.orderId.toString(), envelope)

        latch.await(10, TimeUnit.SECONDS)
        verify(exactly = 1) { notificationService.notifyOrderSuccess(any()) }
    }

    @Test
    fun `order failed - notifyOrderFailure is invoked`() {
        val latch = CountDownLatch(1)
        val payload =
            OrderFailed(
                orderId = UUID.randomUUID(),
                customerId = UUID.randomUUID(),
                reason = "Order processing failed",
            )
        val envelope =
            EventEnvelope(
                eventId = UUID.randomUUID(),
                eventType = "order-failed",
                occurredAt = Instant.now(),
                payload = payload,
            )

        every { notificationService.notifyOrderFailure(any()) } answers {
            callOriginal()
            latch.countDown()
        }

        testKafkaTemplate.send("order-failed", payload.orderId.toString(), envelope)

        latch.await(10, TimeUnit.SECONDS)
        verify(exactly = 1) { notificationService.notifyOrderFailure(any()) }
    }
}

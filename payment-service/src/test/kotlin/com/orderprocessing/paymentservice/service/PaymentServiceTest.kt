package com.orderprocessing.paymentservice.service

import com.orderprocessing.paymentservice.configuration.PaymentProperties
import com.orderprocessing.paymentservice.entities.Payment
import com.orderprocessing.paymentservice.enums.PaymentStatus
import com.orderprocessing.paymentservice.repositories.PaymentRepository
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.events.PaymentProcessed
import com.orderprocessing.shared.events.PaymentRetry
import com.orderprocessing.shared.model.OrderItem
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Tag("unit")
@ExtendWith(MockKExtension::class)
class PaymentServiceTest {
    @MockK lateinit var kafkaTemplate: KafkaTemplate<String, EventEnvelope<*>>

    @MockK lateinit var redisTemplate: RedisTemplate<String, String>

    @MockK lateinit var paymentProperties: PaymentProperties

    @MockK lateinit var paymentRepository: PaymentRepository

    private lateinit var paymentService: PaymentService

    @BeforeEach
    fun setUp() {
        paymentService = PaymentService(paymentProperties, paymentRepository, kafkaTemplate, redisTemplate)
    }

    private fun orderPlacedEnvelope(
        orderId: UUID = UUID.randomUUID(),
        customerId: UUID = UUID.randomUUID(),
    ) = EventEnvelope(
        eventId = UUID.randomUUID(),
        eventType = "order-placed",
        occurredAt = Instant.now(),
        payload =
            OrderPlaced(
                orderId = orderId,
                customerId = customerId,
                items = listOf(OrderItem(UUID.randomUUID(), 2, BigDecimal("10.00"))),
                totalPrice = BigDecimal("20.00"),
            ),
    )

    private fun paymentRetryEnvelope(
        orderId: UUID = UUID.randomUUID(),
        customerId: UUID = UUID.randomUUID(),
        attempts: Int,
    ) = EventEnvelope(
        eventId = UUID.randomUUID(),
        eventType = "payment-retry",
        occurredAt = Instant.now(),
        payload =
            PaymentRetry(
                orderId = orderId,
                customerId = customerId,
                items = listOf(OrderItem(UUID.randomUUID(), 2, BigDecimal("10.00"))),
                totalPrice = BigDecimal("20.00"),
                attempts = attempts,
            ),
    )

    private fun paymentWithStatus(
        orderId: UUID,
        customerId: UUID,
        status: PaymentStatus,
    ) = Payment().apply {
        this.orderId = orderId
        this.customerId = customerId
        this.status = status
        attempts = 1
    }

    @Suppress("UNCHECKED_CAST")
    private fun completedFuture() =
        CompletableFuture.completedFuture(mockk<SendResult<String, EventEnvelope<PaymentProcessed>>>())
            as CompletableFuture<SendResult<String, EventEnvelope<*>>>

    @Test
    fun `processPayment - happy path - saves payment and publishes payment-processed event`() {
        val envelope = orderPlacedEnvelope()
        val orderId = envelope.payload.orderId
        val customerId = envelope.payload.customerId
        val saved = paymentWithStatus(orderId, customerId, PaymentStatus.SUCCESS).also { it.transactionId = UUID.randomUUID() }

        every { redisTemplate.hasKey(any()) } returns false
        every { paymentRepository.findByOrderId(any()) } returns paymentWithStatus(orderId, customerId, PaymentStatus.RETRYING)
        every { paymentProperties.failureRate } returns 0.0
        every { redisTemplate.opsForValue().set(any(), any(), any(), any()) } just Runs
        every { paymentRepository.save(any()) } returns saved
        every { kafkaTemplate.send(any(), any(), any()) } returns completedFuture()

        paymentService.processPayment(envelope)

        verify(exactly = 1) { kafkaTemplate.send("payment-processed", orderId.toString(), any()) }
        verify(exactly = 1) { redisTemplate.hasKey("idempotency:payment:$orderId") }
        verify(exactly = 1) { redisTemplate.opsForValue().set("idempotency:payment:$orderId", "processed", 24, TimeUnit.HOURS) }
        verify(exactly = 1) { paymentRepository.save(any()) }
        verify(exactly = 1) { paymentRepository.findByOrderId(orderId) }
    }

    @Test
    fun `processPayment - failure path - saves payment with retrying status and publishes payment-retry event`() {
        val envelope = orderPlacedEnvelope()
        val orderId = envelope.payload.orderId
        val customerId = envelope.payload.customerId
        val saved = paymentWithStatus(orderId, customerId, PaymentStatus.RETRYING)

        every { redisTemplate.hasKey(any()) } returns false
        every { paymentRepository.findByOrderId(any()) } returns paymentWithStatus(orderId, customerId, PaymentStatus.RETRYING)
        every { paymentProperties.failureRate } returns 1.0
        every { paymentRepository.save(any()) } returns saved
        every { kafkaTemplate.send(any(), any(), any()) } returns completedFuture()

        paymentService.processPayment(envelope)

        verify(exactly = 1) { kafkaTemplate.send("payment-retry", orderId.toString(), any()) }
        verify(exactly = 1) { redisTemplate.hasKey("idempotency:payment:$orderId") }
        verify(exactly = 0) { redisTemplate.opsForValue() }
        verify(exactly = 1) { paymentRepository.save(any()) }
        verify(exactly = 1) { paymentRepository.findByOrderId(orderId) }
    }

    @Test
    fun `processPayment - redis short circuit - already processed so skips processing`() {
        val envelope = orderPlacedEnvelope()
        val orderId = envelope.payload.orderId

        every { redisTemplate.hasKey(any()) } returns true

        paymentService.processPayment(envelope)

        verify(exactly = 0) { kafkaTemplate.send(any(), any(), any()) }
        verify(exactly = 0) { redisTemplate.opsForValue() }
        verify(exactly = 1) { redisTemplate.hasKey("idempotency:payment:$orderId") }
        verify(exactly = 0) { paymentRepository.save(any()) }
        verify(exactly = 0) { paymentRepository.findByOrderId(any()) }
    }

    @Test
    fun `processPayment - db short circuit - terminal status in db so skips processing`() {
        val envelope = orderPlacedEnvelope()
        val orderId = envelope.payload.orderId
        val customerId = envelope.payload.customerId

        every { redisTemplate.hasKey(any()) } returns false
        every { paymentRepository.findByOrderId(any()) } returns paymentWithStatus(orderId, customerId, PaymentStatus.SUCCESS)

        paymentService.processPayment(envelope)

        verify(exactly = 0) { kafkaTemplate.send(any(), any(), any()) }
        verify(exactly = 0) { redisTemplate.opsForValue() }
        verify(exactly = 1) { redisTemplate.hasKey("idempotency:payment:$orderId") }
        verify(exactly = 0) { paymentRepository.save(any()) }
        verify(exactly = 1) { paymentRepository.findByOrderId(orderId) }
    }

    @Test
    fun `processRetry - happy path - succeeds on retry attempt`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val envelope = paymentRetryEnvelope(orderId, customerId, attempts = 1)
        val existing = paymentWithStatus(orderId, customerId, PaymentStatus.RETRYING)
        val saved = paymentWithStatus(orderId, customerId, PaymentStatus.SUCCESS).also { it.transactionId = UUID.randomUUID() }

        every { redisTemplate.hasKey(any()) } returns false
        every { paymentRepository.findByOrderId(any()) } returns existing
        every { paymentProperties.failureRate } returns 0.0
        every { paymentProperties.retry.maxAttempts } returns 3
        every { paymentProperties.retry.delayMs } returns 0
        every { redisTemplate.opsForValue().set(any(), any(), any(), any()) } just Runs
        every { paymentRepository.save(any()) } returns saved
        every { kafkaTemplate.send(any(), any(), any()) } returns completedFuture()

        paymentService.processRetry(envelope)

        verify(exactly = 1) { kafkaTemplate.send("payment-processed", orderId.toString(), any()) }
        verify(exactly = 1) { redisTemplate.hasKey("idempotency:payment:$orderId") }
        verify(exactly = 1) { redisTemplate.opsForValue().set("idempotency:payment:$orderId", "processed", 24, TimeUnit.HOURS) }
        verify(exactly = 2) { paymentRepository.findByOrderId(orderId) }
        verify(exactly = 1) { paymentRepository.save(any()) }
    }

    @Test
    fun `processRetry - failure path - fails again with attempts remaining`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val envelope = paymentRetryEnvelope(orderId, customerId, attempts = 2)
        val existing = paymentWithStatus(orderId, customerId, PaymentStatus.RETRYING)

        every { redisTemplate.hasKey(any()) } returns false
        every { paymentRepository.findByOrderId(any()) } returns existing
        every { paymentProperties.failureRate } returns 1.0
        every { paymentProperties.retry.maxAttempts } returns 3
        every { paymentProperties.retry.delayMs } returns 0
        every { paymentRepository.save(any()) } returns existing
        every { kafkaTemplate.send(any(), any(), any()) } returns completedFuture()

        paymentService.processRetry(envelope)

        verify(exactly = 1) { kafkaTemplate.send("payment-retry", orderId.toString(), any()) }
        verify(exactly = 1) { redisTemplate.hasKey("idempotency:payment:$orderId") }
        verify(exactly = 0) { redisTemplate.opsForValue().set(any(), any(), any(), any()) }
        verify(exactly = 2) { paymentRepository.findByOrderId(orderId) }
        verify(exactly = 1) { paymentRepository.save(any()) }
    }

    @Test
    fun `processRetry - exhausted - max attempts reached publishes order-failed`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val envelope = paymentRetryEnvelope(orderId, customerId, attempts = 3)
        val existing = paymentWithStatus(orderId, customerId, PaymentStatus.RETRYING)

        every { redisTemplate.hasKey(any()) } returns false
        every { paymentRepository.findByOrderId(any()) } returns existing
        every { paymentProperties.retry.maxAttempts } returns 3
        every { paymentRepository.save(any()) } returns existing
        every { kafkaTemplate.send(any(), any(), any()) } returns completedFuture()

        paymentService.processRetry(envelope)

        verify(exactly = 1) { kafkaTemplate.send("order-failed", orderId.toString(), any()) }
        verify(exactly = 1) { redisTemplate.hasKey("idempotency:payment:$orderId") }
        verify(exactly = 0) { redisTemplate.opsForValue().set(any(), any(), any(), any()) }
        verify(exactly = 2) { paymentRepository.findByOrderId(orderId) }
        verify(exactly = 1) { paymentRepository.save(any()) }
    }
}

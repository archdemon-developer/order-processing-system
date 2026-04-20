package com.orderprocessing.paymentservice.service

import com.orderprocessing.paymentservice.configuration.PaymentProperties
import com.orderprocessing.paymentservice.entities.Payment
import com.orderprocessing.paymentservice.enums.PaymentStatus
import com.orderprocessing.paymentservice.repositories.PaymentRepository
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderFailed
import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.events.PaymentProcessed
import com.orderprocessing.shared.events.PaymentRetry
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Service
class PaymentService(
    private val paymentProperties: PaymentProperties,
    private val paymentRepository: PaymentRepository,
    private val kafkaTemplate: KafkaTemplate<String, EventEnvelope<*>>,
    private val redisTemplate: RedisTemplate<String, String>,
) {
    fun processPayment(envelope: EventEnvelope<OrderPlaced>) {
        if (isAlreadyProcessed(envelope.payload.orderId)) {
            return
        }

        if (simulatePayment()) {
            val successPayment = buildPayment(envelope.payload, PaymentStatus.SUCCESS, UUID.randomUUID())
            handleSuccess(successPayment)
            return
        }

        val failedPayment = buildPayment(envelope.payload, PaymentStatus.RETRYING, null)
        paymentRepository.save(failedPayment)
        try {
            kafkaTemplate.send(
                "payment-retry",
                failedPayment.orderId.toString(),
                buildEnvelope(
                    "payment-retry",
                    PaymentRetry(
                        orderId = envelope.payload.orderId,
                        customerId = envelope.payload.customerId,
                        items = envelope.payload.items,
                        totalPrice = envelope.payload.totalPrice,
                        attempts = 1,
                    ),
                ),
            ).get()
        } catch (e: ExecutionException) {
            throw RuntimeException("Failed to publish payment retry event", e.cause)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("Interrupted while publishing payment retry event", e)
        }
    }

    fun processRetry(envelope: EventEnvelope<PaymentRetry>) {
        if (isAlreadyProcessed(envelope.payload.orderId)) {
            return
        }

        val earlierPayment =
            paymentRepository.findByOrderId(envelope.payload.orderId) ?: error("No payment found for orderId: ${envelope.payload.orderId}")

        if (envelope.payload.attempts >= paymentProperties.retry.maxAttempts) {
            earlierPayment.status = PaymentStatus.FAILED
            paymentRepository.save(earlierPayment)
            try {
                kafkaTemplate.send(
                    "order-failed",
                    earlierPayment.orderId.toString(),
                    buildEnvelope(
                        "order-failed",
                        OrderFailed(
                            orderId = envelope.payload.orderId,
                            customerId = envelope.payload.customerId,
                            reason = "Order processing failed",
                        ),
                    ),
                ).get()
            } catch (e: ExecutionException) {
                throw RuntimeException("Failed to publish payment retry event", e.cause)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RuntimeException("Interrupted while publishing payment retry event", e)
            }
            return
        }

        Thread.sleep(
            paymentProperties.retry.delayMs.milliseconds
                .toJavaDuration(),
        )
        if (simulatePayment()) {
            earlierPayment.status = PaymentStatus.SUCCESS
            earlierPayment.transactionId = UUID.randomUUID()
            handleSuccess(earlierPayment)
            return
        }

        earlierPayment.attempts = envelope.payload.attempts + 1
        paymentRepository.save(earlierPayment)
        try {
            kafkaTemplate
                .send(
                    "payment-retry",
                    earlierPayment.orderId.toString(),
                    buildEnvelope(
                        "payment-retry",
                        PaymentRetry(
                            orderId = envelope.payload.orderId,
                            customerId = envelope.payload.customerId,
                            items = envelope.payload.items,
                            totalPrice = envelope.payload.totalPrice,
                            attempts = envelope.payload.attempts + 1,
                        ),
                    ),
                ).get()
        } catch (e: ExecutionException) {
            throw RuntimeException("Failed to publish payment retry event", e.cause)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("Interrupted while publishing payment retry event", e)
        }
    }

    private fun handleSuccess(
        payment: Payment,
    ) {
        paymentRepository.save(payment)

        redisTemplate
            .opsForValue()
            .set("idempotency:payment:$payment.orderId", "processed", 24, TimeUnit.HOURS)
        try {
            kafkaTemplate.send(
                "payment-processed",
                payment.orderId.toString(),
                buildEnvelope(
                    "payment-processed",
                    PaymentProcessed(
                        orderId = payment.orderId,
                        transactionId = payment.transactionId!!,
                        customerId = payment.customerId,
                    ),
                ),
            ).get()
        } catch (e: ExecutionException) {
            throw RuntimeException("Failed to publish payment retry event", e.cause)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("Interrupted while publishing payment retry event", e)
        }
    }

    private fun buildPayment(
        orderPlaced: OrderPlaced,
        paymentStatus: PaymentStatus,
        txId: UUID?,
    ): Payment =
        Payment().apply {
            orderId = orderPlaced.orderId
            customerId = orderPlaced.customerId
            transactionId = txId
            status = paymentStatus
            attempts = 1
        }

    private fun isAlreadyProcessed(orderId: UUID): Boolean {
        if (redisTemplate.hasKey("idempotency:payment:$orderId") ?: false) return true
        val payment = paymentRepository.findByOrderId(orderId) ?: return false
        return payment.status.isTerminal
    }

    private fun simulatePayment(): Boolean = Random.nextDouble() > paymentProperties.failureRate

    private fun <T> buildEnvelope(
        eventType: String,
        payload: T,
    ): EventEnvelope<T> =
        EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = eventType,
            payload = payload,
            occurredAt = Instant.now(),
        )
}

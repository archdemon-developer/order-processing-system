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
import java.util.concurrent.TimeUnit
import kotlin.random.Random

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
            handleSuccess(
                envelope.payload.orderId,
                envelope.payload.customerId,
                buildPayment(envelope.payload, PaymentStatus.SUCCESS, UUID.randomUUID()),
            )
            return
        }

        val failedPayment = buildPayment(envelope.payload, PaymentStatus.RETRYING, null)
        paymentRepository.save(failedPayment)
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
        )
    }

    fun processRetry(envelope: EventEnvelope<PaymentRetry>) {
        if (isAlreadyProcessed(envelope.payload.orderId)) {
            return
        }

        val earlierPayment = paymentRepository.findByOrderId(envelope.payload.orderId).orElseThrow()

        if (envelope.payload.attempts >= paymentProperties.retry.maxAttempts) {
            earlierPayment.status = PaymentStatus.FAILED
            paymentRepository.save(earlierPayment)
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
            )
            return
        }

        Thread.sleep(paymentProperties.retry.delayMs)
        if (simulatePayment()) {
            handleSuccess(envelope.payload.orderId, customerId = envelope.payload.customerId, earlierPayment)
            return
        }

        earlierPayment.attempts = envelope.payload.attempts + 1
        paymentRepository.save(earlierPayment)
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
    }

    private fun handleSuccess(
        orderId: UUID,
        customerId: UUID,
        payment: Payment,
    ) {
        paymentRepository.save(payment)

        redisTemplate
            .opsForValue()
            .set("idempotency:payment:$orderId", "processed", 24, TimeUnit.HOURS)
        kafkaTemplate.send(
            "payment-processed",
            payment.orderId.toString(),
            buildEnvelope(
                "payment-processed",
                PaymentProcessed(
                    orderId = orderId,
                    transactionId = UUID.randomUUID(),
                    customerId = customerId,
                ),
            ),
        )
    }

    private fun buildPayment(
        orderPlaced: OrderPlaced,
        paymentStatus: PaymentStatus,
        transactionId: UUID?,
    ): Payment =
        Payment().apply {
            this.orderId = orderPlaced.orderId
            this.customerId = orderPlaced.customerId
            this.transactionId = transactionId
            this.status = paymentStatus
            this.attempts = 1
        }

    private fun isAlreadyProcessed(orderId: UUID): Boolean {
        if (redisTemplate.hasKey("idempotency:payment:$orderId") == true) return true
        val payment = paymentRepository.findByOrderId(orderId).orElse(null) ?: return false
        return payment.status == PaymentStatus.SUCCESS || payment.status == PaymentStatus.FAILED
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

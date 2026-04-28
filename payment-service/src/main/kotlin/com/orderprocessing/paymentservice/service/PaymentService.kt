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
import com.orderprocessing.shared.outbox.OutboxEvent
import com.orderprocessing.shared.outbox.OutboxEventRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Service
class PaymentService(
    private val paymentProperties: PaymentProperties,
    private val paymentRepository: PaymentRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: JsonMapper,
    private val redisTemplate: RedisTemplate<String, String>,
) {
    @Transactional
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
        outboxEventRepository.save(
            buildOutboxEvent(
                aggregatetype = "payment-retry",
                aggregateid = envelope.payload.orderId.toString(),
                type = "PaymentRetry",
                payload =
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

    @Transactional
    fun processRetry(envelope: EventEnvelope<PaymentRetry>) {
        if (isAlreadyProcessed(envelope.payload.orderId)) {
            return
        }

        val earlierPayment =
            paymentRepository.findByOrderId(envelope.payload.orderId) ?: error("No payment found for orderId: ${envelope.payload.orderId}")

        if (envelope.payload.attempts >= paymentProperties.retry.maxAttempts) {
            earlierPayment.status = PaymentStatus.FAILED
            paymentRepository.save(earlierPayment)
            outboxEventRepository.save(
                buildOutboxEvent(
                    aggregatetype = "order-failed",
                    aggregateid = envelope.payload.orderId.toString(),
                    type = "OrderFailed",
                    payload =
                        OrderFailed(
                            orderId = envelope.payload.orderId,
                            customerId = envelope.payload.customerId,
                            reason = "Order processing failed",
                        ),
                ),
            )
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
        outboxEventRepository.save(
            buildOutboxEvent(
                aggregatetype = "payment-retry",
                aggregateid = envelope.payload.orderId.toString(),
                type = "PaymentRetry",
                payload =
                    PaymentRetry(
                        orderId = envelope.payload.orderId,
                        customerId = envelope.payload.customerId,
                        items = envelope.payload.items,
                        totalPrice = envelope.payload.totalPrice,
                        attempts = envelope.payload.attempts + 1,
                    ),
            ),
        )
    }

    @Transactional
    private fun handleSuccess(payment: Payment) {
        paymentRepository.save(payment)
        redisTemplate
            .opsForValue()
            .set("idempotency:payment:${payment.orderId}", "processed", 24, TimeUnit.HOURS)
        outboxEventRepository.save(
            buildOutboxEvent(
                aggregatetype = "payment-processed",
                aggregateid = payment.orderId.toString(),
                type = "PaymentProcessed",
                payload =
                    PaymentProcessed(
                        orderId = payment.orderId,
                        transactionId = payment.transactionId!!,
                        customerId = payment.customerId,
                    ),
            ),
        )
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

    private fun buildOutboxEvent(
        aggregatetype: String,
        aggregateid: String,
        type: String,
        payload: Any,
    ): OutboxEvent =
        OutboxEvent().apply {
            this.aggregatetype = aggregatetype
            this.aggregateid = aggregateid
            this.type = type
            this.payload =
                objectMapper.writeValueAsString(
                    EventEnvelope(
                        eventId = UUID.randomUUID(),
                        eventType = aggregatetype,
                        occurredAt = Instant.now(),
                        payload = payload,
                    ),
                )
        }

    private fun isAlreadyProcessed(orderId: UUID): Boolean {
        if (redisTemplate.hasKey("idempotency:payment:$orderId") ?: false) return true
        val payment = paymentRepository.findByOrderId(orderId) ?: return false
        return payment.status.isTerminal
    }

    private fun simulatePayment(): Boolean = Random.nextDouble() > paymentProperties.failureRate
}

package com.orderprocessing.payments.entities

import com.orderprocessing.payments.enums.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payments")
class Payment {
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID()

    @Column(name = "order_id", nullable = false)
    lateinit var orderId: UUID

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: PaymentStatus = PaymentStatus.PENDING

    @Column(name = "customer_id")
    lateinit var customerId: UUID

    @Column(name = "transaction_id")
    var transactionId: UUID? = null

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "total_price", nullable = false)
    var totalPrice: BigDecimal = BigDecimal.ZERO
}

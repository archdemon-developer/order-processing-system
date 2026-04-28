package com.orderprocessing.paymentservice.repositories

import com.orderprocessing.paymentservice.entities.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PaymentRepository : JpaRepository<Payment, UUID> {
    fun findByOrderId(orderId: UUID): Payment?
}

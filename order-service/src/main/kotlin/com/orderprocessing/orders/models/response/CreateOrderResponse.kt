package com.orderprocessing.orders.models.response

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateOrderResponse(
    val orderId: UUID,
    val status: String,
    val totalPrice: BigDecimal,
    val createdAt: Instant,
)

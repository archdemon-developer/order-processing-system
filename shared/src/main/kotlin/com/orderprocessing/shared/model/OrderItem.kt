package com.orderprocessing.shared.model

import java.math.BigDecimal
import java.util.UUID

data class OrderItem(
    val productId: UUID,
    val quantity: Int,
    val pricePerItem: BigDecimal,
)

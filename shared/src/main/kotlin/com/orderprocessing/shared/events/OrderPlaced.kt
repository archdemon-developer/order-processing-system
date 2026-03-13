package com.orderprocessing.shared.events

import com.orderprocessing.shared.model.OrderItem
import java.math.BigDecimal
import java.util.UUID

data class OrderPlaced(
    val orderId: UUID,
    val customerId: UUID,
    val items: List<OrderItem>,
    var totalPrice: BigDecimal
)
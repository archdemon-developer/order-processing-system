package com.orderprocessing.shared.events

import java.util.UUID

data class OrderFailed(
    val orderId: UUID,
    val customerId: UUID,
    val reason: String
)
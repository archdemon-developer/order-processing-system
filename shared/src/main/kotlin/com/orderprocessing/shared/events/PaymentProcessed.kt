package com.orderprocessing.shared.events

import java.util.UUID

data class PaymentProcessed(
    val orderId: UUID,
    val transactionId: UUID,
    val customerId: UUID,
)

package com.orderprocessing.shared.events

import com.orderprocessing.shared.model.OrderItem

import java.util.UUID

data class InventoryReserved(
    val orderId: UUID,
    val customerId: UUID,
    val items: List<OrderItem>
)
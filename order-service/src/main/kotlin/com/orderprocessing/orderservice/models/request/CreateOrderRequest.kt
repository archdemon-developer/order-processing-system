package com.orderprocessing.orderservice.models.request

import com.orderprocessing.shared.model.OrderItem
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class CreateOrderRequest(
    @field:NotNull val customerId: UUID,
    @field:NotEmpty @field:Valid val items: List<OrderItemRequest>)
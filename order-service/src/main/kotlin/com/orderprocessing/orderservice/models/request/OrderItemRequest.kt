package com.orderprocessing.orderservice.models.request

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

data class OrderItemRequest (
    @field:NotNull val productId: UUID,
    @field:Min(1) val quantity: Int,
    @field:DecimalMin("0.01") val pricePerItem: BigDecimal)
package com.orderprocessing.orderservice.entities

import com.orderprocessing.orderservice.enums.OrderStatus
import com.orderprocessing.shared.model.OrderItem
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orders")
class Order {

    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID()

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID = UUID.randomUUID()

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: OrderStatus = OrderStatus.PENDING

    @Column(name = "total_price", nullable = false)
    var totalPrice: BigDecimal = BigDecimal.ZERO

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Type(JsonType::class)
    @Column(columnDefinition = "jsonb", nullable = false)
    var items: List<OrderItem> = emptyList()
}
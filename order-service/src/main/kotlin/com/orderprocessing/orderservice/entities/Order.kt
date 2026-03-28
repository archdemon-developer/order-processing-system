package com.orderprocessing.orderservice.entities

import com.orderprocessing.orderservice.converters.OrderItemsConverter
import com.orderprocessing.orderservice.enums.OrderStatus
import com.orderprocessing.shared.model.OrderItem
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
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

    @Convert(converter = OrderItemsConverter::class)
    @Column(name = "items", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    var items: List<OrderItem> = emptyList()
}

package com.orderprocessing.orders.repositories

import com.orderprocessing.orders.entities.Order
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID>

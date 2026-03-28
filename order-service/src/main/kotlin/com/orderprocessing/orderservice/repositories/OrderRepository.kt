package com.orderprocessing.orderservice.repositories

import com.orderprocessing.orderservice.entities.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID>

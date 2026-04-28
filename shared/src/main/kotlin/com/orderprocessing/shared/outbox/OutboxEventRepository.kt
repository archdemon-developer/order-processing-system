package com.orderprocessing.shared.outbox

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {
    fun deleteByCreatedatBefore(cutoff: Instant)
}

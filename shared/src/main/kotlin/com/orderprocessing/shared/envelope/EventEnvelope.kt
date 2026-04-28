package com.orderprocessing.shared.envelope

import java.time.Instant
import java.util.UUID

data class EventEnvelope<T>(
    val eventId: UUID,
    val eventType: String,
    val occurredAt: Instant,
    val payload: T,
    val schemaVersion: Int = 1,
)

package com.orderprocessing.shared.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "outbox_events")
class OutboxEvent {
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID()

    @Column(name = "aggregatetype", nullable = false)
    lateinit var aggregatetype: String

    @Column(name = "aggregateid", nullable = false)
    lateinit var aggregateid: String

    @Column(name = "type", nullable = false)
    lateinit var type: String

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    lateinit var payload: String

    @Column(name = "createdat", nullable = false)
    var createdat: Instant = Instant.now()
}

package com.orderprocessing.shared.envelope

import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.model.OrderItem
import com.orderprocessing.shared.serialization.EventDeserializer
import com.orderprocessing.shared.serialization.EventSerializer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Tag("unit")
class EventEnvelopeCompatibilityTest {
    private val objectMapper =
        JsonMapper
            .builder()
            .addModule(kotlinModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()

    @Test
    fun `deserialises old payload without schemaVersion - defaults to 1`() {
        val oldPayload =
            """
            {
                "eventId": "${UUID.randomUUID()}",
                "eventType": "order-placed",
                "occurredAt": "${Instant.now()}",
                "payload": {
                    "orderId": "${UUID.randomUUID()}",
                    "customerId": "${UUID.randomUUID()}",
                    "items": [],
                    "totalPrice": "20.00"
                }
            }
            """.trimIndent()

        val envelope =
            objectMapper.readValue(
                oldPayload,
                object : TypeReference<EventEnvelope<OrderPlaced>>() {},
            )

        assertEquals(1, envelope.schemaVersion)
        assertEquals("order-placed", envelope.eventType)
    }

    @Test
    fun `serialiser and deserialiser round trip preserves schemaVersion`() {
        val envelope =
            EventEnvelope(
                eventId = UUID.randomUUID(),
                eventType = "order-placed",
                occurredAt = Instant.now(),
                payload =
                    OrderPlaced(
                        orderId = UUID.randomUUID(),
                        customerId = UUID.randomUUID(),
                        items = listOf(OrderItem(UUID.randomUUID(), 2, BigDecimal("10.00"))),
                        totalPrice = BigDecimal("20.00"),
                    ),
                schemaVersion = 2,
            )

        val serialiser = EventSerializer<EventEnvelope<OrderPlaced>>()
        val bytes = serialiser.serialize("order-placed", envelope)

        val deserialiser = EventDeserializer(object : TypeReference<EventEnvelope<OrderPlaced>>() {})
        val result = deserialiser.deserialize("order-placed", bytes)

        Assertions.assertEquals(2, result?.schemaVersion)
        assertEquals("order-placed", result?.eventType)
    }
}

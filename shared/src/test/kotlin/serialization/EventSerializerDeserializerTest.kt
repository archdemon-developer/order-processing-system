package serialization

import com.fasterxml.jackson.core.type.TypeReference
import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.InventoryReserved
import com.orderprocessing.shared.events.OrderFailed
import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.events.PaymentProcessed
import com.orderprocessing.shared.model.OrderItem
import com.orderprocessing.shared.serialization.EventDeserializer
import com.orderprocessing.shared.serialization.EventSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class EventSerializerDeserializerTest {

    private val serializer = EventSerializer<EventEnvelope<*>>()
    private val topic = "test-topic"

    @Test
    fun `should serialize and deserialize OrderPlaced envelope with full payload assertion`() {
        val productId = UUID.randomUUID()
        val payload = OrderPlaced(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            items = listOf(
                OrderItem(
                    productId = productId,
                    quantity = 2,
                    pricePerItem = BigDecimal("15.00")
                )
            ),
            totalPrice = BigDecimal("30.00")
        )
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "OrderPlaced",
            occurredAt = Instant.now(),
            payload = payload
        )

        val deserializer = EventDeserializer(object : TypeReference<EventEnvelope<OrderPlaced>>() {})
        val bytes = serializer.serialize(topic, envelope)
        val result = deserializer.deserialize(topic, bytes)

        assertEquals(envelope.eventId, result?.eventId)
        assertEquals(envelope.eventType, result?.eventType)
        assertEquals(envelope.occurredAt, result?.occurredAt)
        assertEquals(payload.orderId, result?.payload?.orderId)
        assertEquals(payload.customerId, result?.payload?.customerId)
        assertEquals(payload.totalPrice, result?.payload?.totalPrice)
        assertEquals(1, result?.payload?.items?.size)
        assertEquals(productId, result?.payload?.items?.first()?.productId)
        assertEquals(2, result?.payload?.items?.first()?.quantity)
        assertEquals(BigDecimal("15.00"), result?.payload?.items?.first()?.pricePerItem)
    }

    @Test
    fun `should serialize and deserialize PaymentProcessed envelope with full payload assertion`() {
        val transactionId = UUID.randomUUID()
        val payload = PaymentProcessed(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            transactionId = transactionId
        )
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "PaymentProcessed",
            occurredAt = Instant.now(),
            payload = payload
        )

        val deserializer = EventDeserializer(object : TypeReference<EventEnvelope<PaymentProcessed>>() {})
        val bytes = serializer.serialize(topic, envelope)
        val result = deserializer.deserialize(topic, bytes)

        assertEquals(envelope.eventId, result?.eventId)
        assertEquals(envelope.eventType, result?.eventType)
        assertEquals(envelope.occurredAt, result?.occurredAt)
        assertEquals(payload.orderId, result?.payload?.orderId)
        assertEquals(payload.customerId, result?.payload?.customerId)
        assertEquals(transactionId, result?.payload?.transactionId)
    }

    @Test
    fun `should serialize and deserialize OrderFailed envelope with full payload assertion`() {
        val payload = OrderFailed(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            reason = "Insufficient funds"
        )
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "OrderFailed",
            occurredAt = Instant.now(),
            payload = payload
        )

        val deserializer = EventDeserializer(object : TypeReference<EventEnvelope<OrderFailed>>() {})
        val bytes = serializer.serialize(topic, envelope)
        val result = deserializer.deserialize(topic, bytes)

        assertEquals(envelope.eventId, result?.eventId)
        assertEquals(envelope.eventType, result?.eventType)
        assertEquals(envelope.occurredAt, result?.occurredAt)
        assertEquals(payload.orderId, result?.payload?.orderId)
        assertEquals(payload.customerId, result?.payload?.customerId)
        assertEquals("Insufficient funds", result?.payload?.reason)
    }

    @Test
    fun `should serialize and deserialize InventoryReserved envelope with full payload assertion`() {
        val productId = UUID.randomUUID()
        val payload = InventoryReserved(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            items = listOf(
                OrderItem(
                    productId = productId,
                    quantity = 3,
                    pricePerItem = BigDecimal("20.00")
                )
            )
        )
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "InventoryReserved",
            occurredAt = Instant.now(),
            payload = payload
        )

        val deserializer = EventDeserializer(object : TypeReference<EventEnvelope<InventoryReserved>>() {})
        val bytes = serializer.serialize(topic, envelope)
        val result = deserializer.deserialize(topic, bytes)

        assertEquals(envelope.eventId, result?.eventId)
        assertEquals(envelope.eventType, result?.eventType)
        assertEquals(envelope.occurredAt, result?.occurredAt)
        assertEquals(payload.orderId, result?.payload?.orderId)
        assertEquals(payload.customerId, result?.payload?.customerId)
        assertEquals(1, result?.payload?.items?.size)
        assertEquals(productId, result?.payload?.items?.first()?.productId)
        assertEquals(3, result?.payload?.items?.first()?.quantity)
        assertEquals(BigDecimal("20.00"), result?.payload?.items?.first()?.pricePerItem)
    }

    @Test
    fun `should return null when deserializing null bytes`() {
        val deserializer = EventDeserializer(object : TypeReference<EventEnvelope<OrderPlaced>>() {})
        val result = deserializer.deserialize(topic, null)
        assertNull(result)
    }

    @Test
    fun `should produce equal results when deserializing same bytes twice`() {
        val payload = OrderPlaced(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            items = listOf(OrderItem(UUID.randomUUID(), 2, BigDecimal("15.00"))),
            totalPrice = BigDecimal("30.00")
        )
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "OrderPlaced",
            occurredAt = Instant.now(),
            payload = payload
        )

        val deserializer = EventDeserializer(object : TypeReference<EventEnvelope<OrderPlaced>>() {})
        val bytes = serializer.serialize(topic, envelope)
        val result1 = deserializer.deserialize(topic, bytes)
        val result2 = deserializer.deserialize(topic, bytes)

        assertEquals(result1, result2)
    }

    @Test
    fun `should serialize and deserialize OrderPlaced with multiple items`() {
        val item1 = OrderItem(UUID.randomUUID(), 2, BigDecimal("15.00"))
        val item2 = OrderItem(UUID.randomUUID(), 5, BigDecimal("10.00"))
        val item3 = OrderItem(UUID.randomUUID(), 1, BigDecimal("99.99"))

        val payload = OrderPlaced(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            items = listOf(item1, item2, item3),
            totalPrice = BigDecimal("174.99")
        )
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "OrderPlaced",
            occurredAt = Instant.now(),
            payload = payload
        )

        val deserializer = EventDeserializer(object : TypeReference<EventEnvelope<OrderPlaced>>() {})
        val bytes = serializer.serialize(topic, envelope)
        val result = deserializer.deserialize(topic, bytes)

        assertEquals(3, result?.payload?.items?.size)
        assertEquals(item1.productId, result?.payload?.items?.get(0)?.productId)
        assertEquals(item2.productId, result?.payload?.items?.get(1)?.productId)
        assertEquals(item3.productId, result?.payload?.items?.get(2)?.productId)
    }

    @Test
    fun `should preserve BigDecimal precision through serialization`() {
        val payload = OrderPlaced(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            items = listOf(OrderItem(UUID.randomUUID(), 1, BigDecimal("15.999"))),
            totalPrice = BigDecimal("15.999")
        )
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "OrderPlaced",
            occurredAt = Instant.now(),
            payload = payload
        )

        val deserializer = EventDeserializer(object : TypeReference<EventEnvelope<OrderPlaced>>() {})
        val bytes = serializer.serialize(topic, envelope)
        val result = deserializer.deserialize(topic, bytes)

        assertEquals(BigDecimal("15.999"), result?.payload?.totalPrice)
        assertEquals(BigDecimal("15.999"), result?.payload?.items?.first()?.pricePerItem)
    }

    @Test
    fun `should preserve Instant precision through serialization`() {
        val now = Instant.now()
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "OrderPlaced",
            occurredAt = now,
            payload = OrderPlaced(
                orderId = UUID.randomUUID(),
                customerId = UUID.randomUUID(),
                items = listOf(OrderItem(UUID.randomUUID(), 1, BigDecimal("10.00"))),
                totalPrice = BigDecimal("10.00")
            )
        )

        val deserializer = EventDeserializer(object : TypeReference<EventEnvelope<OrderPlaced>>() {})
        val bytes = serializer.serialize(topic, envelope)
        val result = deserializer.deserialize(topic, bytes)

        assertEquals(now, result?.occurredAt)
    }

    @Test
    fun `should preserve eventType casing and value through serialization`() {
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "OrderPlaced",
            occurredAt = Instant.now(),
            payload = OrderPlaced(
                orderId = UUID.randomUUID(),
                customerId = UUID.randomUUID(),
                items = listOf(OrderItem(UUID.randomUUID(), 1, BigDecimal("10.00"))),
                totalPrice = BigDecimal("10.00")
            )
        )

        val deserializer = EventDeserializer(object : TypeReference<EventEnvelope<OrderPlaced>>() {})
        val bytes = serializer.serialize(topic, envelope)
        val result = deserializer.deserialize(topic, bytes)

        assertEquals("OrderPlaced", result?.eventType)
        assertNotEquals("orderplaced", result?.eventType)
        assertNotEquals("ORDERPLACED", result?.eventType)
    }
}
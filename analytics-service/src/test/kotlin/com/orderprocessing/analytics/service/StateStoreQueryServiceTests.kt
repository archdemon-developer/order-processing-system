package com.orderprocessing.analytics.service

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.kstream.Windowed
import org.apache.kafka.streams.state.KeyValueIterator
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.apache.kafka.streams.state.ReadOnlyWindowStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.kafka.config.StreamsBuilderFactoryBean

@Tag("unit")
@ExtendWith(MockKExtension::class)
class StateStoreQueryServiceTests {
    @MockK lateinit var streamsBuilderFactoryBean: StreamsBuilderFactoryBean

    @MockK lateinit var kafkaStreams: KafkaStreams

    @MockK
    lateinit var keyValueStore: ReadOnlyKeyValueStore<String, Long>

    @MockK lateinit var windowStore: ReadOnlyWindowStore<String, Long>

    private lateinit var service: StateStoreQueryService

    @BeforeEach
    fun setUp() {
        service = StateStoreQueryService(streamsBuilderFactoryBean)
        every { streamsBuilderFactoryBean.kafkaStreams } returns kafkaStreams
    }

    private fun <K, V> iteratorOf(vararg pairs: Pair<K, V>): KeyValueIterator<K, V> {
        val list = pairs.map { KeyValue(it.first, it.second) }.iterator()
        return object : KeyValueIterator<K, V> {
            override fun hasNext() = list.hasNext()

            override fun next() = list.next()

            override fun close() {}

            override fun peekNextKey(): K = throw UnsupportedOperationException()

            override fun remove() = throw UnsupportedOperationException()
        }
    }

    @Test
    fun `getTopProducts - returns counts from store`() {
        every { kafkaStreams.store(any<StoreQueryParameters<ReadOnlyKeyValueStore<String, Long>>>()) } returns keyValueStore
        every { keyValueStore.all() } returns iteratorOf("product-1" to 3L, "product-2" to 1L)

        val result = service.getTopProducts()

        assertEquals(2, result.size)
        assertEquals(3L, result["product-1"])
        assertEquals(1L, result["product-2"])
    }

    @Test
    fun `getPaymentOutcomes - returns counts from store`() {
        every { kafkaStreams.store(any<StoreQueryParameters<ReadOnlyKeyValueStore<String, Long>>>()) } returns keyValueStore
        every { keyValueStore.all() } returns iteratorOf("SUCCESS" to 5L, "FAILED" to 2L)

        val result = service.getPaymentOutcomes()

        assertEquals(2, result.size)
        assertEquals(5L, result["SUCCESS"])
        assertEquals(2L, result["FAILED"])
    }

    @Test
    fun `getConfirmedRevenue - returns total from store`() {
        val revenueStore = mockk<ReadOnlyKeyValueStore<String, String>>()
        every { kafkaStreams.store(any<StoreQueryParameters<ReadOnlyKeyValueStore<String, String>>>()) } returns revenueStore
        every { revenueStore["total"] } returns "150.00"

        val result = service.getConfirmedRevenue()

        assertEquals("150.00", result)
    }

    @Test
    fun `getConfirmedRevenue - returns 0 when store is empty`() {
        val revenueStore = mockk<ReadOnlyKeyValueStore<String, String>>()
        every { kafkaStreams.store(any<StoreQueryParameters<ReadOnlyKeyValueStore<String, String>>>()) } returns revenueStore
        every { revenueStore["total"] } returns null

        val result = service.getConfirmedRevenue()

        assertEquals("0", result)
    }

    @Test
    fun `getOrdersPerMinute - returns counts from window store`() {
        every { kafkaStreams.store(any<StoreQueryParameters<ReadOnlyWindowStore<String, Long>>>()) } returns windowStore
        val windowedKey = mockk<Windowed<String>>()
        every { windowedKey.key() } returns "order-1"
        every { windowStore.fetchAll(any(), any()) } returns iteratorOf(windowedKey to 1L)

        val result = service.getOrdersPerMinute()

        assertEquals(1, result.size)
        assertEquals(1L, result["order-1"])
    }

    @Test
    fun `throws when KafkaStreams not initialized`() {
        every { streamsBuilderFactoryBean.kafkaStreams } returns null

        assertThrows<IllegalStateException> {
            service.getTopProducts()
        }
    }
}

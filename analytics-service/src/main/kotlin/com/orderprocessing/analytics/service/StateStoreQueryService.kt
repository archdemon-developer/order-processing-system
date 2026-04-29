package com.orderprocessing.analytics.service

import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class StateStoreQueryService(
    private val streamsBuilderFactoryBean: StreamsBuilderFactoryBean,
) {
    private val kafkaStreams: KafkaStreams
        get() =
            streamsBuilderFactoryBean.kafkaStreams
                ?: throw IllegalStateException("KafkaStreams not initialized")

    fun getOrdersPerMinute(): Map<String, Long> {
        val store =
            kafkaStreams.store(
                StoreQueryParameters.fromNameAndType("orders-per-minute", QueryableStoreTypes.windowStore<String, Long>()),
            )
        val result = mutableMapOf<String, Long>()
        val now = Instant.now()
        store.fetchAll(now.minus(Duration.ofMinutes(1)), now).forEachRemaining { kv ->
            result[kv.key.key()] = kv.value
        }
        return result
    }

    fun getTopProducts(): Map<String, Long> = readKeyValueStore("top-products")

    fun getPaymentOutcomes(): Map<String, Long> = readKeyValueStore("payment-outcomes")

    fun getConfirmedRevenue(): String {
        val store =
            kafkaStreams.store(
                StoreQueryParameters.fromNameAndType("confirmed-revenue", QueryableStoreTypes.keyValueStore<String, String>()),
            )
        return store["total"] ?: "0"
    }

    private fun readKeyValueStore(storeName: String): Map<String, Long> {
        val store =
            kafkaStreams.store(
                StoreQueryParameters.fromNameAndType(storeName, QueryableStoreTypes.keyValueStore<String, Long>()),
            )
        val result = mutableMapOf<String, Long>()
        store.all().forEachRemaining { kv -> result[kv.key] = kv.value }
        return result
    }
}

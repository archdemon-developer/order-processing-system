package com.orderprocessing.analytics.topology

import com.orderprocessing.shared.envelope.EventEnvelope
import com.orderprocessing.shared.events.OrderPlaced
import com.orderprocessing.shared.events.PaymentProcessed
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.TimeWindows
import org.springframework.kafka.config.KafkaStreamsInfrastructureCustomizer
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.Duration

@Component
class AnalyticsTopology(
    private val objectMapper: JsonMapper,
) : StreamsBuilderFactoryBeanConfigurer {
    override fun configure(factoryBean: StreamsBuilderFactoryBean) {
        factoryBean.setInfrastructureCustomizer(
            object : KafkaStreamsInfrastructureCustomizer {
                override fun configureBuilder(builder: StreamsBuilder) {
                    buildTopology(builder)
                }
            },
        )
    }

    private fun buildTopology(builder: StreamsBuilder) {
        val orderPlacedStream: KStream<String, String> = builder.stream("order-placed")

        orderPlacedStream
            .groupByKey()
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
            .count(Materialized.`as`("orders-per-minute"))

        orderPlacedStream
            .flatMap { _, value ->
                val envelope = objectMapper.readValue(value, object : TypeReference<EventEnvelope<OrderPlaced>>() {})
                envelope.payload.items.map { item ->
                    KeyValue(item.productId.toString(), item.productId.toString())
                }
            }.groupByKey()
            .count(Materialized.`as`("top-products"))

        val paymentProcessedStream: KStream<String, String> = builder.stream("payment-processed")
        val orderFailedStream: KStream<String, String> = builder.stream("order-failed")

        val successStream: KStream<String, String> =
            paymentProcessedStream
                .mapValues { _ -> "SUCCESS" }
                .selectKey { _, _ -> "SUCCESS" }

        val failureStream: KStream<String, String> =
            orderFailedStream
                .mapValues { _ -> "FAILED" }
                .selectKey { _, _ -> "FAILED" }

        successStream
            .merge(failureStream)
            .groupByKey()
            .count(Materialized.`as`("payment-outcomes"))

        paymentProcessedStream
            .mapValues { value ->
                val envelope = objectMapper.readValue(value, object : TypeReference<EventEnvelope<PaymentProcessed>>() {})
                envelope.payload.totalPrice.toPlainString()
            }.selectKey { _, _ -> "total" }
            .groupByKey()
            .aggregate(
                { "0" },
                { _, value, accumulator ->
                    BigDecimal(accumulator).add(BigDecimal(value)).toPlainString()
                },
                Materialized.`as`("confirmed-revenue"),
            )
    }
}

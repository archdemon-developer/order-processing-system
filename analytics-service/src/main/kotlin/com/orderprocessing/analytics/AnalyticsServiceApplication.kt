package com.orderprocessing.analytics

import com.orderprocessing.analytics.configuration.AnalyticsProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafkaStreams

@EnableKafkaStreams
@EnableConfigurationProperties(AnalyticsProperties::class)
@SpringBootApplication(scanBasePackages = ["com.orderprocessing"])
class AnalyticsServiceApplication

fun main(args: Array<String>) {
    runApplication<AnalyticsServiceApplication>(*args)
}

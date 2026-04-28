package com.orderprocessing.notificationservice

import com.orderprocessing.notificationservice.configuration.NotificationProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka

@EnableKafka
@SpringBootApplication
@EnableConfigurationProperties(NotificationProperties::class)
class NotificationServiceApplication

fun main(args: Array<String>) {
    runApplication<NotificationServiceApplication>(*args)
}

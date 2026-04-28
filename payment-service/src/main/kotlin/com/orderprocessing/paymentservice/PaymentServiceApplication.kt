package com.orderprocessing.paymentservice

import com.orderprocessing.paymentservice.configuration.ConnectProperties
import com.orderprocessing.paymentservice.configuration.OutboxProperties
import com.orderprocessing.paymentservice.configuration.PaymentProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableScheduling

@EnableKafka
@SpringBootApplication(scanBasePackages = ["com.orderprocessing"])
@EnableJpaRepositories(basePackages = ["com.orderprocessing"])
@EntityScan(basePackages = ["com.orderprocessing"])
@EnableScheduling
@EnableConfigurationProperties(PaymentProperties::class, OutboxProperties::class, ConnectProperties::class)
class PaymentServiceApplication

fun main(args: Array<String>) {
    runApplication<PaymentServiceApplication>(*args)
}

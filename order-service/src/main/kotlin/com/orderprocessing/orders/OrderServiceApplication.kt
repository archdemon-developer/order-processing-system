package com.orderprocessing.orders

import com.orderprocessing.orders.configuration.ConnectProperties
import com.orderprocessing.orders.configuration.OutboxProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@EnableConfigurationProperties(OutboxProperties::class, ConnectProperties::class)
@SpringBootApplication(scanBasePackages = ["com.orderprocessing"])
@EnableJpaRepositories(basePackages = ["com.orderprocessing"])
@EntityScan(basePackages = ["com.orderprocessing"])
class OrderServiceApplication

fun main(args: Array<String>) {
    runApplication<OrderServiceApplication>(*args)
}

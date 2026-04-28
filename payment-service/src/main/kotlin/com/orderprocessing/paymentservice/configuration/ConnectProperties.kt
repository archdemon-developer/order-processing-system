package com.orderprocessing.paymentservice.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("connect")
data class ConnectProperties(
    val url: String,
    val connectorNames: List<String>,
)

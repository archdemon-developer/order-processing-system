package com.orderprocessing.payments.configuration

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.web.client.RestClient

class ConnectHealthIndicator(
    private val connectorName: String,
    private val connectUrl: String,
    private val restClient: RestClient = RestClient.create(),
) : HealthIndicator {
    override fun health(): Health {
        try {
            val connectorStatusResponse =
                restClient
                    .get()
                    .uri(
                        "$connectUrl/connectors/$connectorName/status",
                    ).retrieve()
                    .body(ConnectorStatusResponse::class.java)
            val state = connectorStatusResponse?.connector?.state ?: "UNKNOWN"
            if (state == "RUNNING") {
                return Health.up().build()
            }
            return Health.down().withDetail("state", state).build()
        } catch (e: Exception) {
            return Health.down().withException(e).build()
        }
    }
}

data class ConnectorStatusResponse(
    val connector: ConnectorState,
)

data class ConnectorState(
    val state: String,
)

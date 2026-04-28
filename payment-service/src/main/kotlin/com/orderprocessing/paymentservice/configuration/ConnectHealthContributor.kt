package com.orderprocessing.paymentservice.configuration

import org.springframework.boot.health.contributor.CompositeHealthContributor
import org.springframework.boot.health.contributor.HealthContributor
import org.springframework.boot.health.contributor.HealthContributors
import org.springframework.stereotype.Component
import java.util.stream.Stream

@Component
class ConnectHealthContributor(
    private val connectProperties: ConnectProperties,
) : CompositeHealthContributor {
    private val indicators: Map<String, ConnectHealthIndicator> =
        connectProperties.connectorNames.associateWith { name ->
            ConnectHealthIndicator(name, connectProperties.url)
        }

    override fun getContributor(name: String): HealthContributor? = indicators[name]

    override fun stream(): Stream<HealthContributors.Entry> =
        indicators.entries
            .map { HealthContributors.Entry(it.key, it.value) }
            .stream()
}

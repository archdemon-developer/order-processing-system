package com.orderprocessing.orderservice.configuration

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Health
import org.springframework.web.client.RestClient

@Tag("unit")
class ConnectorHealthIndicatorTest {
    private val restClient = mockk<RestClient>()
    private val requestHeadersUriSpec = mockk<RestClient.RequestHeadersUriSpec<*>>()
    private val requestHeadersSpec = mockk<RestClient.RequestHeadersSpec<*>>()
    private val responseSpec = mockk<RestClient.ResponseSpec>()

    private val indicator =
        ConnectHealthIndicator(
            connectorName = "debezium-orders-outbox",
            connectUrl = "http://localhost:8083",
            restClient = restClient,
        )

    @BeforeEach
    fun setUp() {
        every { restClient.get() } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersSpec
        every { requestHeadersSpec.retrieve() } returns responseSpec
    }

    @Test
    fun `health returns UP when connector state is RUNNING`() {
        every { responseSpec.body(ConnectorStatusResponse::class.java) } returns
            ConnectorStatusResponse(ConnectorState("RUNNING"))

        assertEquals(Health.up().build(), indicator.health())
    }

    @Test
    fun `health returns DOWN when connector state is FAILED`() {
        every { responseSpec.body(ConnectorStatusResponse::class.java) } returns
            ConnectorStatusResponse(ConnectorState("FAILED"))

        val health = indicator.health()
        assertEquals("DOWN", health.status.code)
        assertEquals("FAILED", health.details["state"])
    }

    @Test
    fun `health returns DOWN when REST call throws`() {
        every { responseSpec.body(ConnectorStatusResponse::class.java) } throws RuntimeException("Connection refused")

        val health = indicator.health()
        assertEquals("DOWN", health.status.code)
    }
}

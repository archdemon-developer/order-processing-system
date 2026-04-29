package com.orderprocessing.analytics.controller

import com.orderprocessing.analytics.service.StateStoreQueryService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@Tag("unit")
@ExtendWith(MockKExtension::class)
class AnalyticsControllerTests {
    @MockK
    lateinit var stateStoreQueryService: StateStoreQueryService

    private lateinit var controller: AnalyticsController

    @BeforeEach
    fun setUp() {
        controller = AnalyticsController(stateStoreQueryService)
    }

    @Test
    fun `getOrdersPerMinute - returns 200 with data from service`() {
        every { stateStoreQueryService.getOrdersPerMinute() } returns mapOf("order-1" to 1L)
        val result = controller.getOrdersPerMinute()
        assertEquals(200, result.statusCode.value())
        assertEquals(mapOf("order-1" to 1L), result.body)
    }

    @Test
    fun `getTopProducts - returns 200 with data from service`() {
        every { stateStoreQueryService.getTopProducts() } returns mapOf("product-1" to 3L)
        val result = controller.getTopProducts()
        assertEquals(200, result.statusCode.value())
        assertEquals(mapOf("product-1" to 3L), result.body)
    }

    @Test
    fun `getPaymentOutcomes - returns 200 with data from service`() {
        every { stateStoreQueryService.getPaymentOutcomes() } returns mapOf("SUCCESS" to 5L)
        val result = controller.getPaymentOutcomes()
        assertEquals(200, result.statusCode.value())
        assertEquals(mapOf("SUCCESS" to 5L), result.body)
    }

    @Test
    fun `getConfirmedRevenue - returns 200 with data from service`() {
        every { stateStoreQueryService.getConfirmedRevenue() } returns "150.00"
        val result = controller.getConfirmedRevenue()
        assertEquals(200, result.statusCode.value())
        assertEquals("150.00", result.body)
    }
}

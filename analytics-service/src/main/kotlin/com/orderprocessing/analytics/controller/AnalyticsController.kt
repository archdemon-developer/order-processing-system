package com.orderprocessing.analytics.controller

import com.orderprocessing.analytics.service.StateStoreQueryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController(
    private val stateStoreQueryService: StateStoreQueryService,
) {
    @GetMapping("/orders-per-minute")
    fun getOrdersPerMinute(): ResponseEntity<Map<String, Long>> = ResponseEntity.ok(stateStoreQueryService.getOrdersPerMinute())

    @GetMapping("/top-products")
    fun getTopProducts(): ResponseEntity<Map<String, Long>> = ResponseEntity.ok(stateStoreQueryService.getTopProducts())

    @GetMapping("/payment-outcomes")
    fun getPaymentOutcomes(): ResponseEntity<Map<String, Long>> = ResponseEntity.ok(stateStoreQueryService.getPaymentOutcomes())

    @GetMapping("/confirmed-revenue")
    fun getConfirmedRevenue(): ResponseEntity<String> = ResponseEntity.ok(stateStoreQueryService.getConfirmedRevenue())
}

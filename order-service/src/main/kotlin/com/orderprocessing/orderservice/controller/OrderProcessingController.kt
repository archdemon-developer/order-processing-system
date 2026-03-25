package com.orderprocessing.orderservice.controller

import com.orderprocessing.orderservice.models.request.CreateOrderRequest
import com.orderprocessing.orderservice.models.response.CreateOrderResponse
import com.orderprocessing.orderservice.service.OrderService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderProcessingController (private val orderService: OrderService){

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun processOrder(@Valid @RequestBody createOrderRequest: CreateOrderRequest): ResponseEntity<CreateOrderResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(createOrderRequest))
    }
}
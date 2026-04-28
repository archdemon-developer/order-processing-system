package com.orderprocessing.paymentservice.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): JsonMapper =
        JsonMapper
            .builder()
            .addModule(kotlinModule())
            .build()
}

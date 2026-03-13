package com.orderprocessing.shared.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.common.serialization.Serializer

class EventSerializer<T>: Serializer<T> {

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule());

    override fun serialize(topic: String, data: T): ByteArray? {
        if (data == null) return null
        return objectMapper.writeValueAsBytes(data)
    }
}
package com.orderprocessing.shared.serialization

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.common.serialization.Deserializer

class EventDeserializer<T>(private val typeReference: TypeReference<T>): Deserializer<T> {

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    override fun deserialize(topic: String, data: ByteArray?): T? {
        if  (data == null) return null
        return objectMapper.readValue(data, typeReference)
    }
}
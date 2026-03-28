package com.orderprocessing.shared.serialization

import org.apache.kafka.common.serialization.Serializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

class EventSerializer<T> : Serializer<T> {
    private val objectMapper =
        JsonMapper
            .builder()
            .addModule(kotlinModule())
            .build()

    override fun serialize(
        topic: String,
        data: T,
    ): ByteArray? {
        if (data == null) return null
        return objectMapper.writeValueAsBytes(data)
    }
}

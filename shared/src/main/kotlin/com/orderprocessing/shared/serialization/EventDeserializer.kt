package com.orderprocessing.shared.serialization

import org.apache.kafka.common.serialization.Deserializer
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

class EventDeserializer<T>(
    private val typeReference: TypeReference<T>,
) : Deserializer<T> {
    private val objectMapper =
        JsonMapper
            .builder()
            .addModule(kotlinModule())
            .build()

    override fun deserialize(
        topic: String,
        data: ByteArray?,
    ): T? {
        val safeData = data ?: return null
        return objectMapper.readValue(safeData, typeReference)
    }
}

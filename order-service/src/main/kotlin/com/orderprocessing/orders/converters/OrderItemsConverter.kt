package com.orderprocessing.orders.converters

import com.orderprocessing.shared.model.OrderItem
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

@Converter(autoApply = false)
class OrderItemsConverter : AttributeConverter<List<OrderItem>, String> {
    private val objectMapper =
        JsonMapper
            .builder()
            .addModule(kotlinModule())
            .build()

    private val typeReference = object : TypeReference<List<OrderItem>>() {}

    override fun convertToDatabaseColumn(attribute: List<OrderItem>): String? = objectMapper.writeValueAsString(attribute)

    override fun convertToEntityAttribute(dbData: String): List<OrderItem> = objectMapper.readValue(dbData, typeReference)
}

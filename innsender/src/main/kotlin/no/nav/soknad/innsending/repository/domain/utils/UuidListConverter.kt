package no.nav.soknad.innsending.repository.domain.utils

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.io.IOException
import java.util.UUID


@Converter
class UuidListConverter(
	private val objectMapper: ObjectMapper,
) : AttributeConverter<List<UUID>?, String?> {

	override fun convertToDatabaseColumn(attribute: List<UUID>?): String? {
		return try {
			if (attribute == null) null else objectMapper.writeValueAsString(attribute.map { it.toString() })
		} catch (_: JsonProcessingException) {
			null // Handle exception
		}
	}

	override fun convertToEntityAttribute(dbData: String?): List<UUID>? {
		return try {
			if (dbData == null) null else {
				val valueTypeRef = object : TypeReference<List<String>?>() {}
				objectMapper.readValue(dbData, valueTypeRef)
					?.map { UUID.fromString(it) }
			}
		} catch (_: IOException) {
			null // Handle exception
		}
	}
}

package no.nav.soknad.innsending.repository.domain.utils

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.io.IOException


@Converter
class StringListConverter(
	private val objectMapper: ObjectMapper,
) : AttributeConverter<List<String>?, String?> {

	override fun convertToDatabaseColumn(attribute: List<String>?): String? {
		return try {
			if (attribute == null) null else objectMapper.writeValueAsString(attribute)
		} catch (_: JsonProcessingException) {
			null // Handle exception
		}
	}

	override fun convertToEntityAttribute(dbData: String?): List<String>? {
		return try {
			if (dbData == null) emptyList() else objectMapper.readValue(
				dbData,
				object : TypeReference<List<String>?>() {})
		} catch (_: IOException) {
			emptyList() // Handle exception
		}
	}
}

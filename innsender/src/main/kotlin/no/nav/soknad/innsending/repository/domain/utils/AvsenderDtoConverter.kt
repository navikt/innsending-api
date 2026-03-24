package no.nav.soknad.innsending.repository.domain.utils

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import no.nav.soknad.innsending.model.AvsenderDto
import java.io.IOException

@Converter
class AvsenderDtoConverter(
	private val objectMapper: ObjectMapper,
	) : AttributeConverter<AvsenderDto?, String?>{


	override fun convertToDatabaseColumn(attribute: AvsenderDto?): String? {
		return try {
			if (attribute == null) null else objectMapper.writeValueAsString(attribute)
		} catch (_: JsonProcessingException) {
			null // Handle exception
		}
	}

	override fun convertToEntityAttribute(dbData: String?): AvsenderDto? {
		return try {
			if (dbData == null) null
			else {
				objectMapper.readValue(dbData, AvsenderDto::class.java)
			}
		} catch (_: IOException) {
			null // Handle exception
		}
	}

}

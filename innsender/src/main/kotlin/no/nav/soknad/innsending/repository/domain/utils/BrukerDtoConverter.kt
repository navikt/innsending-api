package no.nav.soknad.innsending.repository.domain.utils

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import no.nav.soknad.innsending.model.BrukerDto
import java.io.IOException

@Converter
class BrukerDtoConverter(
	private val objectMapper: ObjectMapper,
	) : AttributeConverter<BrukerDto?, String?> {

	override fun convertToDatabaseColumn(attribute: BrukerDto?): String? {
		return try {
			if (attribute == null) null else objectMapper.writeValueAsString(attribute)
		} catch (_: JsonProcessingException) {
			null // Handle exception
		}
	}

	override fun convertToEntityAttribute(dbData: String?): BrukerDto? {
		return try {
			if (dbData == null) null
			else {
				objectMapper.readValue(dbData, BrukerDto::class.java)
			}
		} catch (_: IOException) {
			null // Handle exception
		}
	}


}

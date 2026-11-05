package no.nav.soknad.innsending.repository.domain.utils

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner

@Converter
class ApplicationOwnerConverter(private val objectMapper: ObjectMapper) : AttributeConverter<ApplicationOwner?, String?> {

	override fun convertToDatabaseColumn(attribute: ApplicationOwner?): String? =
		attribute?.let { objectMapper.writeValueAsString(it) }

	override fun convertToEntityAttribute(dbData: String?): ApplicationOwner? {
		if (dbData == null) return null
		return try {
			val node = objectMapper.reader()
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.readTree(dbData)
			if (node == null || !node.isObject || node.fieldNames().asSequence().toSet() != setOf("id", "idType") ||
				!node.path("id").isTextual || !node.path("idType").isTextual
			) {
				INVALID_OWNER
			} else {
				ApplicationOwner(node.path("id").textValue(), node.path("idType").textValue())
			}
		} catch (_: JsonProcessingException) {
			// Preserve the distinction between an absent owner and malformed stored data.
			INVALID_OWNER
		}
	}

	companion object {
		private val INVALID_OWNER = ApplicationOwner(null, null)
	}
}

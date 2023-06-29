package no.nav.soknad.innsending.repository

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class OpplastingsStatusAttributeConverter : AttributeConverter<OpplastingsStatus, String?> {
	override fun convertToDatabaseColumn(status: OpplastingsStatus): String? {
		return if (status == null) null else status.name
	}

	override fun convertToEntityAttribute(status: String?): OpplastingsStatus? {
		return if (status == null) null else OpplastingsStatus.valueOf(status)
	}
}

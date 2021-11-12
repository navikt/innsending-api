package no.nav.soknad.innsending.repository

import javax.persistence.AttributeConverter
import javax.persistence.Converter

@Converter(autoApply = true)
class SoknadsStatusAttributeConverter : AttributeConverter<SoknadsStatus, String?> {
	override fun convertToDatabaseColumn(status: SoknadsStatus): String? {
		return if (status == null) null else status.name
	}

	override fun convertToEntityAttribute(status: String?): SoknadsStatus? {
		return if (status == null) null else SoknadsStatus.valueOf(status)
	}
}

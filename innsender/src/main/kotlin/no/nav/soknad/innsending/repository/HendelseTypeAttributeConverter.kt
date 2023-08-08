package no.nav.soknad.innsending.repository

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class HendelseTypeAttributeConverter : AttributeConverter<HendelseType, String?> {
	override fun convertToDatabaseColumn(hendelsetype: HendelseType): String {
		return hendelsetype.name
	}

	override fun convertToEntityAttribute(hendelsetype: String?): HendelseType {
		return if (hendelsetype == null) HendelseType.Ukjent else HendelseType.valueOf(hendelsetype)
	}
}


package no.nav.soknad.innsending.repository

import javax.persistence.AttributeConverter
import javax.persistence.Converter

@Converter(autoApply = true)
class HendelseTypeAttributeConverter : AttributeConverter<HendelseType, String?> {
	override fun convertToDatabaseColumn(hendelsetype: HendelseType): String {
		return hendelsetype.name
	}

	override fun convertToEntityAttribute(hendelsetype: String?): HendelseType {
		return if (hendelsetype == null) HendelseType.Ukjent else HendelseType.valueOf(hendelsetype)
	}
}


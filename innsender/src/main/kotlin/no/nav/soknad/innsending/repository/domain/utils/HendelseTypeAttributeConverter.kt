package no.nav.soknad.innsending.repository.domain.utils

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import no.nav.soknad.innsending.repository.domain.enums.HendelseType

@Converter(autoApply = true)
class HendelseTypeAttributeConverter : AttributeConverter<HendelseType, String?> {
	override fun convertToDatabaseColumn(hendelsetype: HendelseType): String {
		return hendelsetype.name
	}

	override fun convertToEntityAttribute(hendelsetype: String?): HendelseType {
		return if (hendelsetype == null) HendelseType.Ukjent else HendelseType.valueOf(hendelsetype)
	}
}


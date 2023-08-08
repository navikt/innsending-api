package no.nav.soknad.innsending.repository

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class ArkiveringsStatusAttributeConverter : AttributeConverter<ArkiveringsStatus, String?> {
	override fun convertToDatabaseColumn(arkiveringsstatus: ArkiveringsStatus): String {
		return if (arkiveringsstatus == null) ArkiveringsStatus.IkkeSatt.name else arkiveringsstatus.name
	}

	override fun convertToEntityAttribute(arkiveringsstatus: String?): ArkiveringsStatus {
		return if (arkiveringsstatus == null) ArkiveringsStatus.IkkeSatt else ArkiveringsStatus.valueOf(arkiveringsstatus)
	}
}

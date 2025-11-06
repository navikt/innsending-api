package no.nav.soknad.innsending.service.config.utils

import no.nav.soknad.innsending.model.ConfigValueDto
import no.nav.soknad.innsending.repository.domain.models.ConfigDbData
import no.nav.soknad.innsending.service.config.ConfigDefinition

fun ConfigDbData.toDto(): ConfigValueDto {
	val def = ConfigDefinition.fromKey(this.key)
	return ConfigValueDto(
		key = this.key,
		value = this.value,
		type = def.type,
		label = def.label,
		description = def.description,
	)
}

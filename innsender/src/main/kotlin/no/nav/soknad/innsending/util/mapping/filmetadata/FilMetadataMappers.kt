package no.nav.soknad.innsending.util.mapping.filmetadata

import no.nav.soknad.innsending.model.FileDto
import no.nav.soknad.innsending.service.fillager.FilMetadata
import no.nav.soknad.innsending.util.stringextensions.toUUID

fun FilMetadata.toDto(): FileDto {
	return FileDto(
		id = this.filId.toUUID(),
		name = this.filnavn,
		propertySize = this.storrelse,
		createdAt = this.createdAt,
	)
}

package no.nav.soknad.innsending.util.models.attachmentdto

import no.nav.soknad.innsending.model.AttachmentDto
import no.nav.soknad.innsending.util.stringextensions.removeInvalidControlCharacters

fun List<AttachmentDto>?.sanitize(): List<AttachmentDto>? {
	return this?.map {
		it.copy(
			title = it.title?.removeInvalidControlCharacters(),
			label = it.label.removeInvalidControlCharacters(),
		)
	}
}

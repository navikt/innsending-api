package no.nav.soknad.innsending.util.soknaddbdata

import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData

fun SoknadDbData.validPersonOwnerId(): String? {
	if (visningstype !in digitalDisplayTypes) return null
	return owner?.personId
}

private val digitalDisplayTypes = setOf(VisningsType.fyllUt, VisningsType.dokumentinnsending, VisningsType.ettersending, VisningsType.lospost)

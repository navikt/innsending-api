package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.innsending.model.LospostDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData

fun mapTilLospost (soknad: SoknadDbData, vedlegg: VedleggDbData): LospostDto {
	assert(soknad.visningstype == VisningsType.lospost)
	return LospostDto(
		brukerId = soknad.brukerid,
		innsendingsId = soknad.innsendingsid,
		tema = soknad.tema,
		tittel = soknad.tittel,
		spraak = soknad.spraak,
		id = soknad.id,
		arkiveringsStatus = mapTilArkiveringsStatusDto(soknad.arkiveringsstatus),
		opprettetDato = mapTilOffsetDateTime(soknad.opprettetdato)!!,
		vedleggsListe = listOf(lagVedleggDto(vedlegg))
	)
}

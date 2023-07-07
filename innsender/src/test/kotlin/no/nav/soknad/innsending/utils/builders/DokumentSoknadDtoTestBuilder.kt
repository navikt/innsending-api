package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.model.*
import java.time.OffsetDateTime
import java.util.*

class DokumentSoknadDtoTestBuilder(
	var brukerId: String = "12128012345",
	var skjemanr: String = "NAV 08-21.05",
	var tittel: String = "Forsikring mot ansvar for sykepenger i arbeidsgiverperioden for små bedrifter.",
	var tema: String = "FOS",
	var status: SoknadsStatusDto = SoknadsStatusDto.opprettet,
	var opprettetDato: OffsetDateTime = OffsetDateTime.now(),
	var vedleggsListe: List<VedleggDto> = listOf(),
	var id: Long? = null,
	var innsendingsId: String? = UUID.randomUUID().toString(),
	var ettersendingsId: String? = null,
	var spraak: String? = "nb_NO",
	var endretDato: OffsetDateTime = OffsetDateTime.now(),
	var innsendtDato: OffsetDateTime? = null,
	var visningsSteg: Long? = 0L,
	var visningsType: VisningsType = VisningsType.fyllUt,
	var kanLasteOppAnnet: Boolean? = null,
	var innsendingsFristDato: OffsetDateTime? = null,
	var forsteInnsendingsDato: OffsetDateTime? = null,
	var fristForEttersendelse: Long? = 14L,
	var arkiveringsStatus: ArkiveringsStatusDto = ArkiveringsStatusDto.ikkeSatt,
	var erSystemGenerert: Boolean? = true
) {

	fun withVedlegg(vedlegg: VedleggDto) = apply { vedleggsListe += listOf(vedlegg) }

	fun build() = DokumentSoknadDto(
		brukerId,
		skjemanr,
		tittel,
		tema,
		status,
		opprettetDato,
		vedleggsListe,
		id,
		innsendingsId,
		ettersendingsId,
		spraak,
		endretDato,
		innsendtDato,
		visningsSteg,
		visningsType,
		kanLasteOppAnnet,
		innsendingsFristDato,
		forsteInnsendingsDato,
		fristForEttersendelse,
		arkiveringsStatus,
		erSystemGenerert
	)
}

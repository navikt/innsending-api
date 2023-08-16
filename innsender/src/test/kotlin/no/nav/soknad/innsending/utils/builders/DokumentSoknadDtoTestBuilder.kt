package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.model.*
import java.time.OffsetDateTime
import java.util.*

class DokumentSoknadDtoTestBuilder(
	var brukerId: String = "12128012345",
	var skjemanr: String = "NAV ${UUID.randomUUID().toString().take(4)}",
	var tittel: String = "Forsikring mot ansvar for sykepenger i arbeidsgiverperioden for små bedrifter.",
	var tema: String = "FOS",
	var status: SoknadsStatusDto = SoknadsStatusDto.opprettet,
	var opprettetDato: OffsetDateTime = OffsetDateTime.now(),
	var vedleggsListe: List<VedleggDto> = listOf(
		VedleggDtoTestBuilder().asHovedDokument().build(),
		VedleggDtoTestBuilder().asHovedDokumentVariant().build()
	),
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
	var erSystemGenerert: Boolean? = true,
	var soknadType: SoknadType? = null
) {

	val erEttersending = ettersendingsId != null || visningsType == VisningsType.ettersending

	fun withVedlegg(vedlegg: VedleggDto) = apply { vedleggsListe += listOf(vedlegg) }

	fun asEttersending(): DokumentSoknadDtoTestBuilder {
		soknadType = SoknadType.ettersendelse
		visningsType = VisningsType.ettersending
		ettersendingsId = UUID.randomUUID().toString()
		return this
	}

	fun build() = DokumentSoknadDto(
		brukerId = brukerId,
		skjemanr = skjemanr,
		tittel = tittel,
		tema = tema,
		status = status,
		opprettetDato = opprettetDato,
		vedleggsListe = vedleggsListe,
		id = id,
		innsendingsId = innsendingsId,
		ettersendingsId = ettersendingsId,
		spraak = spraak,
		endretDato = endretDato,
		innsendtDato = innsendtDato,
		visningsSteg = visningsSteg,
		visningsType = visningsType,
		kanLasteOppAnnet = kanLasteOppAnnet,
		innsendingsFristDato = innsendingsFristDato,
		forsteInnsendingsDato = forsteInnsendingsDato,
		fristForEttersendelse = fristForEttersendelse,
		arkiveringsStatus = arkiveringsStatus,
		erSystemGenerert = erSystemGenerert,
		soknadstype = soknadType ?: (if (erEttersending) SoknadType.ettersendelse else SoknadType.soknad),
	)
}

package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.model.SkjemaDokumentDto
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.util.Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD
import no.nav.soknad.innsending.util.Skjema
import no.nav.soknad.innsending.utils.Skjema.generateSkjemanr
import java.time.OffsetDateTime
import java.util.*


data class SkjemaDtoTestBuilder(
	var brukerId: String = "12128012345",
	var skjemanr: String = generateSkjemanr(),
	var tittel: String = "Forsikring mot ansvar for sykepenger i arbeidsgiverperioden for små bedrifter.",
	var tema: String = "FOS",
	var spraak: String = "nb_NO",
	var hoveddokument: SkjemaDokumentDto = SkjemaDokumentDtoTestBuilder().asHovedDokument(skjemanr).build(),
	var hoveddokumentVariant: SkjemaDokumentDto = SkjemaDokumentDtoTestBuilder().asHovedDokumentVariant(skjemanr).build(),
	var innsendingsId: String? = UUID.randomUUID().toString(),
	var status: SoknadsStatusDto? = SoknadsStatusDto.Opprettet,
	var vedleggsListe: List<SkjemaDokumentDto>? = emptyList(),
	var kanLasteOppAnnet: Boolean? = false,
	var fristForEttersendelse: Long? = 14L,
	var skjemaPath: String = Skjema.createSkjemaPathFromSkjemanr(skjemanr),
	var skalslettesdato: OffsetDateTime? = OffsetDateTime.now().plusDays(DEFAULT_LEVETID_OPPRETTET_SOKNAD),
	var mellomlagringDager: Int? = DEFAULT_LEVETID_OPPRETTET_SOKNAD.toInt()
) {

	fun medBrukerId(brukerId: String) = apply { this.brukerId = brukerId }
	fun medVedlegg(vedlegg: SkjemaDokumentDto) = apply { vedleggsListe = (vedleggsListe ?: emptyList()) + listOf(vedlegg) }
	fun medVedlegg(vedlegg: List<SkjemaDokumentDto>) = apply { vedleggsListe = vedlegg }
	fun medStatus(status: SoknadsStatusDto) = apply { this.status = status }
	fun medInnsendingsId(innsendingsId: String) = apply { this.innsendingsId = innsendingsId }
	fun medFristForEttersendelse(fristForEttersendelse: Long) = apply { this.fristForEttersendelse = fristForEttersendelse }
	fun medSkjemaPath(skjemaPath: String) = apply { this.skjemaPath = skjemaPath }
	fun medSkalSlettesDato(skalslettesdato: OffsetDateTime) = apply { this.skalslettesdato = skalslettesdato }
	fun medMellomlagringDager(mellomlagringDager: Int?) = apply { this.mellomlagringDager = mellomlagringDager }
	fun medHoveddokument(hoveddokument: SkjemaDokumentDto) = apply { this.hoveddokument = hoveddokument }
	fun medHoveddokumentVariant(hoveddokumentVariant: SkjemaDokumentDto) = apply { this.hoveddokumentVariant = hoveddokumentVariant }

	fun build() = SkjemaDto(
		brukerId = brukerId,
		skjemanr = skjemanr,
		tittel = tittel,
		tema = tema,
		spraak = spraak,
		hoveddokument = hoveddokument,
		hoveddokumentVariant = hoveddokumentVariant,
		innsendingsId = innsendingsId,
		status = status,
		vedleggsListe = vedleggsListe,
		kanLasteOppAnnet = kanLasteOppAnnet,
		fristForEttersendelse = fristForEttersendelse,
		skjemaPath = skjemaPath,
		skalSlettesDato = skalslettesdato,
		mellomlagringDager = mellomlagringDager
	)
}


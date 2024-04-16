package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.model.SkjemaDokumentDto
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.util.Skjema
import no.nav.soknad.innsending.utils.Skjema.generateSkjemanr
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
	var skjemaPath: String = Skjema.createSkjemaPathFromSkjemanr(skjemanr)
) {
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
		skjemaPath = skjemaPath
	)
}


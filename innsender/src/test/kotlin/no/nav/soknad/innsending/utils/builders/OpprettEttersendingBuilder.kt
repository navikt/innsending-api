package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.model.InnsendtVedleggDto
import no.nav.soknad.innsending.model.OpprettEttersending
import no.nav.soknad.innsending.utils.Skjema.generateSkjemanr

data class OpprettEttersendingBuilder(
	var brukerId: String = "12128012345",
	var skjemanr: String = generateSkjemanr(),
	var tittel: String = "Forsikring mot ansvar for sykepenger i arbeidsgiverperioden for små bedrifter.",
	var tema: String = "FOS",
	var spraak: String = "nb_NO",
	var vedleggsListe: List<InnsendtVedleggDto>? = emptyList(),
	var innsendingsfristDager: Long? = 14L,
	var mellomlagringDager: Int? = 14,
	) {

	fun medBrukerId(brukerId: String) = apply { this.brukerId = brukerId }
	fun medVedleggGittNr(vedlegg: List<String>) = apply { vedleggsListe = vedlegg.map { mapTilInnsendtVedleggDto(it) } }
	fun medVedlegg(vedlegg: List<InnsendtVedleggDto>?) = apply { vedleggsListe = vedlegg }
	fun medInnsendingsfristDager(innsendingsfristDager: Long) = apply { this.innsendingsfristDager = innsendingsfristDager }
	fun medMellomlagringDager(mellomlagringDager: Int) = apply { this.mellomlagringDager = mellomlagringDager }

	fun build() = OpprettEttersending(
		skjemanr = skjemanr,
		tittel = tittel,
		tema = tema,
		sprak	= spraak,
		vedleggsListe = vedleggsListe,
		innsendingsfristDager = innsendingsfristDager,
		mellomlagringDager = mellomlagringDager

		)

	private fun mapTilInnsendtVedleggDto(vedlegg: String) = InnsendtVedleggDto(
		vedleggsnr = vedlegg,
		tittel = "$vedlegg tittel",
		url = null,
		opplastingsValgKommentarLedetekst = null,
		opplastingsValgKommentar = null
	)
}

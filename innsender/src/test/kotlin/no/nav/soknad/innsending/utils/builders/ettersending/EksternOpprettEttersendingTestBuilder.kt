package no.nav.soknad.innsending.utils.builders.ettersending

import no.nav.soknad.innsending.model.BrukernotifikasjonsType
import no.nav.soknad.innsending.model.EksternOpprettEttersending
import no.nav.soknad.innsending.model.InnsendtVedleggDto
import java.util.*

class EksternOpprettEttersendingTestBuilder {
	private var skjemanr: String = "NAV-${UUID.randomUUID().toString().take(4)}"
	private var sprak: String = "nb_NO"
	private var vedleggsListe: List<InnsendtVedleggDto> = listOf(InnsendtVedleggDtoTestBuilder().build())
	private var tema = "FOS"
	private var tittel = "Ettersendingssøknad tittel"
	private var brukernotifikasjonstype = BrukernotifikasjonsType.utkast

	fun skjemanr(skjemanr: String) = apply { this.skjemanr = skjemanr }
	fun sprak(sprak: String) = apply { this.sprak = sprak }
	fun vedleggsListe(vedleggsListe: List<InnsendtVedleggDto>) = apply { this.vedleggsListe = vedleggsListe }
	fun tema(tema: String) = apply { this.tema = tema }
	fun tittel(titel: String) = apply { this.tittel = titel }
	fun brukernotifikasjonstype(brukernotifikasjonstype: BrukernotifikasjonsType) =
		apply { this.brukernotifikasjonstype = brukernotifikasjonstype }

	fun build(): EksternOpprettEttersending {
		return EksternOpprettEttersending(
			skjemanr = skjemanr,
			sprak = sprak,
			vedleggsListe = vedleggsListe,
			tema = tema,
			tittel = tittel,
			brukernotifikasjonstype = brukernotifikasjonstype,
		)
	}
}

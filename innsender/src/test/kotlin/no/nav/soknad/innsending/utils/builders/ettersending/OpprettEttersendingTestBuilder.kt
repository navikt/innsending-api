package no.nav.soknad.innsending.utils.builders.ettersending

import no.nav.soknad.innsending.model.InnsendtVedleggDto
import no.nav.soknad.innsending.model.OpprettEttersending
import java.util.*

class OpprettEttersendingTestBuilder {
	private var skjemanr: String = "NAV-${UUID.randomUUID().toString().take(4)}"
	private var sprak: String = "nb_NO"
	private var vedleggsListe: List<InnsendtVedleggDto>? = emptyList()

	fun skjemanr(skjemanr: String) = apply { this.skjemanr = skjemanr }
	fun sprak(sprak: String) = apply { this.sprak = sprak }
	fun vedleggsListe(vedleggsListe: List<InnsendtVedleggDto>) = apply { this.vedleggsListe = vedleggsListe }

	fun build(): OpprettEttersending {
		return OpprettEttersending(
			skjemanr = skjemanr,
			sprak = sprak,
			vedleggsListe = vedleggsListe
		)
	}
}

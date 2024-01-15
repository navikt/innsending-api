package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.innsending.model.EksternOpprettEttersending
import no.nav.soknad.innsending.model.OpprettEttersending

fun mapToOpprettEttersending(eksternOpprettEttersending: EksternOpprettEttersending): OpprettEttersending {
	return OpprettEttersending(
		tittel = eksternOpprettEttersending.tittel,
		skjemanr = eksternOpprettEttersending.skjemanr,
		sprak = eksternOpprettEttersending.sprak,
		tema = eksternOpprettEttersending.tema,
		vedleggsListe = eksternOpprettEttersending.vedleggsListe
	)
}

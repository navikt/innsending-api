package no.nav.soknad.innsending.utils.builders.pdl

import no.nav.soknad.innsending.pdl.generated.prefilldata.Postboksadresse

class PostboksadresseTestBuilder {
	private var postbokseier: String? = "NAV"
	private var postboks: String = "Postboks 1184 Sentrum"
	var postnummer: String? = "0107"

	fun postbokseier(postbokseier: String?) = apply { this.postbokseier = postbokseier }
	fun postboks(postboks: String) = apply { this.postboks = postboks }
	fun postnummer(postnummer: String?) = apply { this.postnummer = postnummer }

	fun build() = Postboksadresse(
		postbokseier = postbokseier,
		postboks = postboks,
		postnummer = postnummer
	)
}

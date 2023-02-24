package no.nav.soknad.innsending.utils

import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker
import no.nav.soknad.innsending.repository.SoknadDbData
import java.util.UUID

class ArkivertSakerTestdataBuilder(
	var eksternReferanseId: String = UUID.randomUUID().toString(),
	var tittel: String = "Avtale om barnebidrag",
	var tema: String = "BID",
) {
	fun fromSoknad(soknad: SoknadDbData) = apply {
		this.tittel = soknad.tittel
		this.tema = soknad.tema
		this.eksternReferanseId = soknad.innsendingsid
	}
	fun build() = ArkiverteSaker(eksternReferanseId, tittel, tema, null, emptyList())
}

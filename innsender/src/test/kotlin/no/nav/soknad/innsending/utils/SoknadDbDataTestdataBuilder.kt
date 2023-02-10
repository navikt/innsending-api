package no.nav.soknad.innsending.utils

import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.SoknadDbData
import no.nav.soknad.innsending.repository.SoknadsStatus
import java.time.LocalDateTime
import java.util.*

data class SoknadDbDataTestdataBuilder(
	var brukerId: String = "12345678901",
	var skjemanr: String = "NAV 55-00.60",
	var skjematittel: String = "Avtale om barnebidrag",
	var tema: String = "BID",
	var innsendtdato: LocalDateTime? = null,
	var innsendingsId: String = UUID.randomUUID().toString(),
	var erarkivert: Boolean? = null
) {
	fun brukerId(brukerId: String) = apply { this.brukerId = brukerId }
	fun skjemanr(skjemanr: String) = apply { this.skjemanr = skjemanr }
	fun skjematittel(skjematittel: String) = apply { this.skjematittel = skjematittel }
	fun tema(tema: String) = apply { this.tema = tema }
	fun innsendtdato(innsendtdato: LocalDateTime?) = apply { this.innsendtdato = innsendtdato }
	fun erarkivert(erarkivert: Boolean?) = apply { this.erarkivert = erarkivert }
	fun build() = SoknadDbData(null, innsendingsId, skjematittel, skjemanr, tema, "nb", SoknadsStatus.Innsendt, brukerId, null, LocalDateTime.now(), LocalDateTime.now(), innsendtdato, 0, VisningsType.fyllUt, true, null, 14, this.erarkivert)
}

package no.nav.soknad.innsending.utils

import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import java.time.LocalDateTime
import java.util.*

data class SoknadDbDataTestdataBuilder(
	var brukerId: String = "12345678901",
	var skjemanr: String = "NAV 55-00.60",
	var tittel: String = "Avtale om barnebidrag",
	var tema: String = "BID",
	var innsendtdato: LocalDateTime? = null,
	var innsendingsId: String = UUID.randomUUID().toString(),
	var arkiveringsStatus: ArkiveringsStatus? = ArkiveringsStatus.IkkeSatt
) {
	fun brukerId(brukerId: String) = apply { this.brukerId = brukerId }
	fun skjemanr(skjemanr: String) = apply { this.skjemanr = skjemanr }
	fun tittel(skjematittel: String) = apply { this.tittel = skjematittel }
	fun tema(tema: String) = apply { this.tema = tema }
	fun innsendtdato(innsendtdato: LocalDateTime?) = apply { this.innsendtdato = innsendtdato }
	fun erarkivert(arkiveringsStatus: ArkiveringsStatus) = apply { this.arkiveringsStatus = arkiveringsStatus }
	fun build() = SoknadDbData(
		null, innsendingsId, tittel, skjemanr, tema, "nb", SoknadsStatus.Innsendt, brukerId,
		null, LocalDateTime.now(), LocalDateTime.now(), innsendtdato, 0, VisningsType.fyllUt, true,
		null, 14, this.arkiveringsStatus ?: ArkiveringsStatus.IkkeSatt
	)
}

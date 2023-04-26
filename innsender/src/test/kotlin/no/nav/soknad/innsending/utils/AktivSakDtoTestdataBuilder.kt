package no.nav.soknad.innsending.utils

import no.nav.soknad.innsending.model.AktivSakDto
import no.nav.soknad.innsending.repository.SoknadDbData
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

data class AktivSakDtoTestdataBuilder(
	var skjemanr: String = "NAV 55-00.60",
	var tittel: String = "Avtale om barnebidrag",
	var tema: String = "BID",
	var innsendtdato: OffsetDateTime = LocalDateTime.now().atOffset(ZoneOffset.UTC),
	var innsendingsid: String = UUID.randomUUID().toString(),
) {
	fun fromSoknad(soknad: SoknadDbData) = apply {
		checkNotNull(soknad.innsendtdato) { "En søknad må være innsendt for at en sak skal eksistere i arkivet" }
		this.skjemanr = soknad.skjemanr
		this.tittel = soknad.tittel
		this.tema = soknad.tema
		this.innsendtdato = soknad.innsendtdato!!.atOffset(ZoneOffset.UTC)
		this.innsendingsid = soknad.innsendingsid
	}

	fun build() = AktivSakDto(skjemanr, tittel, tema, innsendtdato, false, emptyList(), innsendingsid)
}

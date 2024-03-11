package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.AktivitetType
import no.nav.soknad.innsending.model.Maalgruppe


interface ArenaConsumerInterface {
	fun getMaalgrupper(): List<Maalgruppe>
	fun getAktiviteter(type: AktivitetType): List<Aktivitet>
}

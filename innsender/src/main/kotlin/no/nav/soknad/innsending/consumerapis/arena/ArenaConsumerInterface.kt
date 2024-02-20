package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.Maalgruppe


interface ArenaConsumerInterface {
	fun getMaalgrupper(): List<Maalgruppe>
	fun getAktiviteter(): List<Aktivitet>
}

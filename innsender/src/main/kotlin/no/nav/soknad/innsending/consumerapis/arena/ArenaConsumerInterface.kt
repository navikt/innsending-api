package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.Maalgruppe

interface ArenaConsumerInterface {
	suspend fun getMaalgrupper(): List<Maalgruppe>
	suspend fun getAktiviteter(): List<Aktivitet>
}

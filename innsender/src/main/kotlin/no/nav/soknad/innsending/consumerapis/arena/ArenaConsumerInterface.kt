package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.consumerapis.arena.dto.Maalgruppe

interface ArenaConsumerInterface {
	fun getMaalgruppe(): List<Maalgruppe>
}

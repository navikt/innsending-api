package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.consumerapis.arena.dto.Maalgruppe

interface ArenaConsumerInterface {
	suspend fun getMaalgruppe(): List<Maalgruppe>
}

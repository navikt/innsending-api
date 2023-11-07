package no.nav.soknad.innsending.consumerapis.arena.dto

import no.nav.soknad.innsending.model.MaalgruppeType


data class Maalgruppe(
	val gyldighetsperiode: Gyldighetsperiode,
	val maalgruppetype: MaalgruppeType,
	val maalgruppenavn: String
)

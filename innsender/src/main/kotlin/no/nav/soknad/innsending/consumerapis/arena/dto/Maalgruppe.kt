package no.nav.soknad.innsending.consumerapis.arena.dto


data class Maalgruppe(
	val gyldighetsperiode: Gyldighetsperiode,
	val maalgruppetype: MaalgruppeType,
	val maalgruppenavn: String
)

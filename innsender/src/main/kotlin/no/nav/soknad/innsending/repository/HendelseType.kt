package no.nav.soknad.innsending.repository

enum class HendelseType {
	Ukjent,
	Opprettet,
	SlettetAvSystem,
	SlettetPermanentAvBruker,
	SlettetPermanentAvSystem,
	Innsendt,
	Arkivert,
	ArkiveringFeilet
}

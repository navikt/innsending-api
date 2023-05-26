package no.nav.soknad.innsending.repository

enum class HendelseType {
	Ukjent,
	Opprettet,
	SlettetAvBruker, // Vil etterhvert utgå og er blitt erstattet SlettetPermanentAvBruker
	SlettetAvSystem,
	SlettetPermanentAvBruker,
	SlettetPermanentAvSystem,
	Innsendt,
	Arkivert,
	ArkiveringFeilet
}

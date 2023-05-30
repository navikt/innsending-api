package no.nav.soknad.innsending.repository

enum class HendelseType {
	Ukjent,
	Opprettet,
	Endret, // Endring på søknad, f.eks. språk
	SlettetAvBruker, // Vil etterhvert utgå og er blitt erstattet SlettetPermanentAvBruker
	SlettetAvSystem,
	SlettetPermanentAvBruker,
	SlettetPermanentAvSystem,
	Innsendt,
	Arkivert,
	ArkiveringFeilet
}

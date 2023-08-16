package no.nav.soknad.innsending.repository.domain.enums

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
	ArkiveringFeilet,
	Utfylt // Ferdig utfylt søknad som ikke er innsendt
}

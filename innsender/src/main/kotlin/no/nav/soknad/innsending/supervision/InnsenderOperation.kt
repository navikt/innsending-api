package no.nav.soknad.innsending.supervision

enum class InnsenderOperation(name: String) {
	UKJENT("Ukjent"),
	OPPRETT("Opprett"),
	HENT("Hent"),
	SLETT("Slett"),
	LAST_OPP("LastOppFil"),
	SLETT_FIL("SlettFil"),
	LAST_NED("LastNedFil"),
	SEND_INN("SendInn"),
	ENDRE("Endre")
}

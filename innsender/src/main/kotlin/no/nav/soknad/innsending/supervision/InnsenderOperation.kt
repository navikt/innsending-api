package no.nav.soknad.innsending.supervision

enum class InnsenderOperation(name: String) {
	UKJENT("Ukjent"),
	OPPRETT("Opprett"),
	HENT("Hent"),
	SLETT("Slett"),
	LAST_OPP("LastOppFil"),
	LAST_OPP_NOLOGIN("LastOppFilNologin"),
	SLETT_FIL("SlettFil"),
	SLETT_FIL_NOLOGIN("SlettFilNologin"),
	SLETT_FILER_NOLOGIN("SlettFilerNologin"),
	LAST_NED("LastNedFil"),
	SEND_INN("SendInn"),
	SEND_INN_NOLOGIN("SendInnNologin"),
	ENDRE("Endre"),
	VIRUS_SCAN("VirusScan"),
}

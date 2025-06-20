package no.nav.soknad.innsending.supervision

enum class InnsenderOperation(name: String) {
	UKJENT("Ukjent"),
	OPPRETT("Opprett"),
	HENT("Hent"),
	SLETT("Slett"),
	LAST_OPP("LastOppFil"),
	LAST_OPP_BUCKET("BucketLastOppFil"),
	SLETT_FIL("SlettFil"),
	SLETT_FIL_BUCKET("BucketSlettFil"),
	SLETT_FILER_BUCKET("BucketSlettFiler"),
	LAST_NED("LastNedFil"),
	SEND_INN("SendInn"),
	ENDRE("Endre"),
	VIRUS_SCAN("VirusScan"),
}

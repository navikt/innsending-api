package no.nav.soknad.innsending.util

import no.nav.soknad.arkivering.soknadsmottaker.model.Soknad

const val testpersonid = "19876898104"

val supportedLanguages = listOf("no", "nb", "nn", "se", "en", "de", "fr", "es", "pl")
val backupLanguage = mapOf(
	"no" to "no",
	"nb" to "no",
	"nn" to "no",
	"se" to "no",
	"en" to "no",
	"de" to "en",
	"fr" to "en",
	"es" to "en",
	"pl" to "en"
)

fun finnSpraakFraInput(spraak: String?): String {
	if (spraak.isNullOrBlank() || spraak.length < 2) return "nb"
	val spraakLowercase = spraak.substring(0, 2).lowercase()
	return if (supportedLanguages.contains(spraakLowercase)) spraakLowercase else "nb"
}

fun finnBackupLanguage(wanted: String): String {
	val spraak = finnSpraakFraInput(wanted)

	return backupLanguage[spraak]!!
}

fun maskerFnr(soknad: Soknad): Soknad {
	return Soknad(soknad.innsendingId, soknad.erEttersendelse, personId = "*****", soknad.tema, soknad.dokumenter)
}

val tilleggsstonad_fiks = listOf(
	"NAV 11-12.15 B", // Støtte til Barnepass
	"NAV 11-12.16 B", // Støtte til Læremidler
	"NAV 11-12.17 B", // Støtte til samling
	"NAV 11-12.18 B", // Støtte til ved oppstart, avslutning eller hjemreiser
	"NAV 11-12.19 B", // Støtte til bolig og overnatting
	"NAV 11-12.21 B", // Støtte til daglig reise
	"NAV 11-12.22 B", // Støtte til reise for å komme i arbeid
	"NAV 11-12.23 B", // Støtte til flytting
)

fun fiksSkjemanr(skjemanr: String): String {
	if (!tilleggsstonad_fiks.contains(skjemanr)) return skjemanr
	return skjemanr.trim().replace(" B", "B")
}


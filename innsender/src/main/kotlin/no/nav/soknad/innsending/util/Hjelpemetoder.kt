package no.nav.soknad.innsending.util

val supportedLanguages = listOf("no", "nn", "se", "en", "de", "fr", "es", "pl")

fun finnSpraakFraInput(spraak: String?): String {
	if (spraak.isNullOrBlank() || spraak.length < 2) return "no"
	val spraakLowercase = spraak.substring(0,1).lowercase()
	return if (supportedLanguages.contains(spraakLowercase)) spraakLowercase else "no"
}

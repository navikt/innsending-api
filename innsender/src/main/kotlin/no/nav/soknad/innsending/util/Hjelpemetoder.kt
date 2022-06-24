package no.nav.soknad.innsending.util

const val testpersonid = "27928799005"

val supportedLanguages = listOf("no", "nb", "nn", "se", "en", "de", "fr", "es", "pl")
val backupLanguage = mapOf( "no" to "no", "nb" to "no", "nn" to "no", "se" to "no", "en" to "no", "de" to "en", "fr" to "en", "es" to "en", "pl" to "en")

fun finnSpraakFraInput(spraak: String?): String {
	if (spraak.isNullOrBlank() || spraak.length < 2) return "nb"
	val spraakLowercase = spraak.substring(0,2).lowercase()
	return if (supportedLanguages.contains(spraakLowercase)) spraakLowercase else "nb"
}

fun finnBackupLanguage(wanted: String): String {
	val spraak = finnSpraakFraInput(wanted)

	return backupLanguage[spraak]!!
}

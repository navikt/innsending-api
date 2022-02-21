package no.nav.soknad.innsending.util

val testpersonid = "02097225454"

val supportedLanguages = listOf("no", "nn", "se", "en", "de", "fr", "es", "pl")
val backupLanguage = mapOf<String, String>( "no" to "no", "nn" to "no", "se" to "no", "en" to "no", "de" to "en", "fr" to "en", "es" to "en", "pl" to "en")

fun finnSpraakFraInput(spraak: String?): String {
	if (spraak.isNullOrBlank() || spraak.length < 2) return "no"
	val spraakLowercase = spraak.substring(0,2).lowercase()
	return if (supportedLanguages.contains(spraakLowercase)) spraakLowercase else "no"
}

fun finnBackupLanguage(wanted: String): String {
	val spraak = finnSpraakFraInput(wanted)

	return backupLanguage.get(spraak)!!
}

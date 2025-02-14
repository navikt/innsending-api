package no.nav.soknad.innsending.util

import no.nav.soknad.arkivering.soknadsmottaker.model.DocumentData
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
	return Soknad(
		soknad.innsendingId,
		soknad.erEttersendelse,
		personId = "*****",
		soknad.tema,
		maskerVedleggsTittel(soknad.dokumenter)
	)
}

fun maskerVedleggsTittel(dokumenter: List<DocumentData>): List<DocumentData> {
	return dokumenter.map {
		DocumentData(
			skjemanummer = it.skjemanummer,
			erHovedskjema = it.erHovedskjema,
			if (it.skjemanummer == "N6") "**Maskert**" else it.tittel,
			it.varianter
		)
	}
}


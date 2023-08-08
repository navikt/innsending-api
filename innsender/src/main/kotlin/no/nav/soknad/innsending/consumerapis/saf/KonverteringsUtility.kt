package no.nav.soknad.innsending.consumerapis.saf

class KonverteringsUtility {

	public fun brevKodeKontroll(brevkode: String?): String {
		// Trimme oppgitt brevkode. Sjekke syntaks og erstatte med N6 hvis ikke korrekt
		val defaultValue = "N6"
		if (brevkode.isNullOrEmpty()) return defaultValue
		val trimmet = brevkode.trimEnd().trimStart()

		//val skjemanrRegex = "NAV.*e{1} \\d{2}-\\d{2}.\\d{2}.*\\S{1}".toRegex()
		val skjemanrRegex = "NAV \\d{2}-\\d{2}.\\d{2}".toRegex()
		val skjemanrRegexEttersending = "NAVe \\d{2}-\\d{2}.\\d{2}".toRegex()
		val skjemanrRegexEkstra = "NAV \\d{2}-\\d{2}.\\d{2}\\S{1}".toRegex()
		val skjemanrRegexEkstraEttersending = "NAVe \\d{2}-\\d{2}.\\d{2}\\S{1}".toRegex()
		val vedleggRegex = "\\S{1}\\d{1}".toRegex()

		if (trimmet.length == 2) {
			if (vedleggRegex.matches(trimmet.uppercase())) {
				return trimmet
			}
		} else if (skjemanrRegex.matches(trimmet) || skjemanrRegexEttersending.matches(trimmet) ||
			skjemanrRegexEkstra.matches(trimmet) || skjemanrRegexEkstraEttersending.matches(trimmet)
		) {
			return trimmet
		}
		return defaultValue
	}

}

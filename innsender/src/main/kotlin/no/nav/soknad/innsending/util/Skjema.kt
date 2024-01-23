package no.nav.soknad.innsending.util

object Skjema {
	fun createSkjemaPathFromSkjemanr(skjemanr: String): String {
		val skjemanrWithoutNonAlphanumeric = removeNonAlphanumeric(skjemanr)

		return skjemanrWithoutNonAlphanumeric.trim().lowercase()
	}

	private fun removeNonAlphanumeric(input: String): String {
		return input.replace("[^a-zA-Z0-9]".toRegex(), "")
	}

}

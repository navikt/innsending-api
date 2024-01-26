package no.nav.soknad.innsending.utils

object Skjema {

	// Generates skjemanr in the format: NAV 10-99.99
	fun generateSkjemanr(): String {
		return "NAV ${(10..99).random()}-${(10..99).random()}.${(10..99).random()}"
	}

	// Generates vedleggsnr in the format: A1
	fun generateVedleggsnr(): String {
		return "${('A'..'Z').random()}${(1..9).random()}"
	}
	
}

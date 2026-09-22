package no.nav.soknad.innsending.utils

object Skjema {

	private val vedleggsnumre = listOf("C1", "L8", "N6", "W1", "W2", "W3", "X1", "Y9")
	private var nesteVedleggsnummer = 0

	// Generates skjemanr in the format: NAV 10-99.99
	fun generateSkjemanr(): String {
		return "NAV ${(10..99).random()}-${(10..99).random()}.${(10..99).random()}"
	}

	// Generates vedleggsnr in the format: A1
	@Synchronized
	fun generateVedleggsnr(): String {
		return vedleggsnumre[nesteVedleggsnummer++ % vedleggsnumre.size]
	}
}

package no.nav.soknad.innsending.service.fillager

enum class FilStatus(val value: String) {

	LASTET_OPP("lastetOpp"),
	SLETTET("slettet");

	companion object {
		fun from(s: String): FilStatus? = entries.firstOrNull { it.value == s }
	}

}

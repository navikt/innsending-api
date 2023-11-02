package no.nav.soknad.innsending.util.extensions

fun List<String>?.ifContains(key: String, value: String?): String? {
	return if (this?.contains(key) == true) value else null
}

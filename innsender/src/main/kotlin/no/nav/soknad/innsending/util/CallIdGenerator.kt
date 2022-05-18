package no.nav.soknad.innsending.util

import java.util.*

class CallIdGenerator {
	fun create(): String {
		return UUID.randomUUID().toString()
	}
}

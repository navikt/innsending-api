package no.nav.soknad.innsending.util

import no.nav.soknad.innsending.util.Constants.CORRELATION_ID
import org.slf4j.MDC
import java.util.*

object MDCUtil {
	private val GEN = CallIdGenerator()

	@JvmStatic
	fun callId(): String {
		return MDC.get(CORRELATION_ID)
	}

	fun callIdOrNew(): String {
		try {
			return callId()
		} catch (ex: Exception) {
			return GEN.create()
		}
	}

	@JvmOverloads
	fun toMDC(key: String?, value: String?, defaultValue: String? = "null") {
		MDC.put(
			key, Optional.ofNullable(value)
				.orElse(defaultValue)
		)
	}

	fun clear() {
		MDC.clear()
	}


}

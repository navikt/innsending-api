package no.nav.soknad.innsending.util

import no.nav.soknad.innsending.util.Constants.CORRELATION_ID
import no.nav.soknad.innsending.util.Constants.MDC_INNSENDINGS_ID
import ulid4j.Ulid
import java.util.*

class Utilities {


	companion object {
		private val ulidGenerator = Ulid()

		fun laginnsendingsId(): String {
			val innsendingsId = UUID.randomUUID().toString()
			MDCUtil.toMDC(CORRELATION_ID, innsendingsId)
			MDCUtil.toMDC(MDC_INNSENDINGS_ID, innsendingsId)
			return innsendingsId
		}

		fun lagUlidId(): String {
			return ulidGenerator.next()
		}
	}


}

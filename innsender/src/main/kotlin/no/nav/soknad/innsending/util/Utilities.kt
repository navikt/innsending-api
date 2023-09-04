package no.nav.soknad.innsending.util

import no.nav.soknad.innsending.util.Constants.CORRELATION_ID
import no.nav.soknad.innsending.util.Constants.MDC_INNSENDINGS_ID
import java.util.*

class Utilities {


	companion object {
		fun laginnsendingsId(): String {
			val innsendingsId = UUID.randomUUID().toString()
			MDCUtil.toMDC(CORRELATION_ID, innsendingsId)
			MDCUtil.toMDC(MDC_INNSENDINGS_ID, innsendingsId)
			return innsendingsId
		}
	}


}

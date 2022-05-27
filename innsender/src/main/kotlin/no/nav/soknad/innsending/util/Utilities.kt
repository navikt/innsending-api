package no.nav.soknad.innsending.util

import no.nav.soknad.innsending.util.Constants.CORRELATION_ID
import java.util.*

class Utilities {


	companion object {
		fun laginnsendingsId(): String {
			val innsendingsId = UUID.randomUUID().toString()
			MDCUtil.toMDC(CORRELATION_ID, innsendingsId)
			return innsendingsId
		}
	/*
		val innsendingAppPrefix = "20"
 		fun laginnsendingsId(databasenokkel: Long): String {
			val base = (innsendingAppPrefix + "0000000").toLong(36)
			val innsendingsId =
				java.lang.Long.toString(base + databasenokkel, 36).uppercase(Locale.getDefault()).replace("O", "o")
					.replace("I", "i")
			if (!innsendingsId.startsWith(innsendingAppPrefix)) {
				throw RuntimeException("Tildelt sekvensrom for innsendingsId er brukt opp. Kan ikke generer innsendingsId $innsendingsId")
			}
			return innsendingsId
		}
*/
	}


}

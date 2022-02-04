package no.nav.soknad.innsending.service

import java.util.*

class Utilities {


	companion object {
		fun laginnsendingsId(): String {
			return UUID.randomUUID().toString()
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

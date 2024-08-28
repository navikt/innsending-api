package no.nav.soknad.innsending.util.mapping.tilleggsstonad

/**
 * TODO må ryddes opp i før ferdigstillelse av jobben med å flytte tilleggsstønader til fyllut-sendinn
 * Innholdet i denne klassen er hentet fra sendsoknad. Den er tatt med som kontroll og i tilfelle det er nødvendig å benytte disse verdiene i forbindelse med mapping fra JSON til XML.
 *
 */
class SammensattAdresse(
	val land: String?,
	val landkode: String?,
	val adresse: String?,
	val postnr: String?,
	val poststed: String? = null,
	val postkode: String? = null
) {
	var sammensattAdresse: String = ""

	init {
		sammensattAdresse =
			if (landkode == null || LANDKODE.equals(landkode, true) || LANDKODE_ALT.equals(landkode, true)) {
				String.format("%s, %s", adresse, postnr + (if (poststed != null) " " + poststed else ""))
			} else {
				String.format(
					"%s, %s, %s",
					adresse,
					(if (postkode != null) postkode else "") + (if (poststed != null) " " + poststed else ""),
					land
				)
			}
	}

	companion object {
		private const val NORGE = "Norge"
		private const val LANDKODE = "NO"
		private const val LANDKODE_ALT = "NOR"
	}
}


enum class BarnepassAarsak(val cmsKey: String) {
	langvarig("Langvarig/uregelmessig fravær på grunn av arbeid eller utdanning"), // fra "soknad.barnepass.fjerdeklasse.aarsak.langvarig"
	trengertilsyn("Barnet mitt har et særlig behov for pass"), // fra "soknad.barnepass.fjerdeklasse.aarsak.tilsyn"
	ingen("Ingen av disse passer") //  fra "soknad.barnepass.fjerdeklasse.aarsak.ingen"
}


enum class FormaalKodeverk(val kodeverksverdi: String) {
	oppfolging("OPPF"),
	jobbintervju("JOBB"),
	tiltraa("TILT")
}


enum class SkolenivaaerKodeverk(val kodeverk: String) {
	videregaende("VGS"),
	hoyereutdanning("HGU"),
	annet("ANN")
}


enum class ErUtgifterDekketKodeverk(val kodeverk: String) {
	ja("JA"),
	nei("NEI"),
	delvis("DEL")
}


enum class InnsendingsintervallerKodeverk(val kodeverksverdi: String) {
	uke("UKE"),
	maned("MND")
}


enum class TilsynForetasAvKodeverk(val kodeverksverdi: String) {
	dagmamma("KOM"),
	barnehage("OFF"),
	privat("PRI")
}


private enum class Maalgrupper {
	NEDSARBEVN,
	ENSFORUTD,
	ENSFORARBS,
	TIDLFAMPL,
	GJENEKUTD,
	GJENEKARBS,
	MOTTILTPEN,
	MOTDAGPEN,
	ARBSOKERE;

	companion object {
		fun toMaalgruppe(kodeverkVerdi: String?): Maalgrupper? {
			return try {
				if (kodeverkVerdi != null)
					Maalgrupper.valueOf(kodeverkVerdi.uppercase())
				else
					null
			} catch (e: IllegalArgumentException) {
				null
			}
		}
	}
}


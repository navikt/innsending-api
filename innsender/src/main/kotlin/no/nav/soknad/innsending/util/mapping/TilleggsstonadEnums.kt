package no.nav.soknad.innsending.util.mapping


class SammensattAdresse(val land: String?, val adresse: String?, val postnr: String?) {
	var sammensattAdresse: String = ""

	init {
		sammensattAdresse = if (land == null || land == NORGE) {
			String.format("%s, %s", adresse, postnr)
		} else {
			String.format("%s, %s", adresse, land)
		}
	}

	companion object {
		private const val NORGE = "Norge"
	}
}


enum class BarnepassAarsak(val cmsKey: String) {
	langvarig("soknad.barnepass.fjerdeklasse.aarsak.langvarig"),
	trengertilsyn("soknad.barnepass.fjerdeklasse.aarsak.tilsyn"),
	ingen("soknad.barnepass.fjerdeklasse.aarsak.ingen")
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


enum class FlytterSelv(val cms: String) {
	flytterselv("soknad.flytting.spm.selvellerbistand.flytterselv"),
	flyttebyraa("soknad.flytting.spm.selvellerbistand.flyttebyraa"),
	tilbudmenflytterselv("soknad.flytting.spm.selvellerbistand.tilbudmenflytterselv")
}


enum class TilsynForetasAv(val stofoString: String) {
	privat("Privat"),
	offentlig("Offentlig"),
	annet("Annet");
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

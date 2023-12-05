package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.IkkeRegistrertAktivitetsperiode
import no.nav.soknad.innsending.util.mapping.JsonBostottesoknad
import no.nav.soknad.innsending.util.mapping.JsonRettighetstyper
import no.nav.soknad.innsending.util.mapping.PeriodeForSamling

class JsonBostotteTestBuilder(
	var fradato: String,
	var tildato: String,
	var boutgiftType: String = "Jeg søker om å få dekket faste boutgifter",
	var bostotteIForbindelseMedSamling: List<PeriodeForSamling>? = null,
	var mottarDuBostotteFraKommunen: String = "Nei",
	var hvilkeAdresserHarDuBoutgifterPa: List<String> = listOf(
		"Jeg har boutgifter på aktivitetsadressen min",
		"Jeg har fortsatt boutgifter på hjemstedet mitt"
	),
	var boutgifterPaAktivitetsadressen: Int = 3500,
	var boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten: Int = 0,
	var erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet: String = "Nei"
) {

	fun buildBostotteSoknad() = JsonBostottesoknad(
		aktivitetsperiode = IkkeRegistrertAktivitetsperiode(startdatoDdMmAaaa = fradato, sluttdatoDdMmAaaa = tildato),
		hvilkeBoutgifterSokerDuOmAFaDekket = boutgiftType,
		bostotteIForbindelseMedSamling = bostotteIForbindelseMedSamling,
		mottarDuBostotteFraKommunen = mottarDuBostotteFraKommunen,
		hvilkeAdresserHarDuBoutgifterPa = hvilkeAdresserHarDuBoutgifterPa,
		boutgifterPaAktivitetsadressen = boutgifterPaAktivitetsadressen,
		boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten = boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten,
		erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet = erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet
	)

	fun build() = JsonRettighetstyper(
		bostotte = buildBostotteSoknad()
	)

}

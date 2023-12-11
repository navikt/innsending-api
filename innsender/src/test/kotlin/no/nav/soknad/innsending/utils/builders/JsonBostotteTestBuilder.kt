package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.IkkeRegistrertAktivitetsperiode
import no.nav.soknad.innsending.util.mapping.JsonBostottesoknad
import no.nav.soknad.innsending.util.mapping.JsonRettighetstyper
import no.nav.soknad.innsending.util.mapping.PeriodeForSamling
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonBostotteTestBuilder {

	protected var fradato: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	protected var tildato: String = Date.formatToLocalDate(LocalDateTime.now().plusMonths(3))
	protected var boutgiftType: String = "Jeg søker om å få dekket faste boutgifter"
	protected var bostotteIForbindelseMedSamling: List<PeriodeForSamling>? = null
	protected var mottarDuBostotteFraKommunen: String = "Nei"
	protected var hvilkeAdresserHarDuBoutgifterPa: List<String> = listOf(
		"Jeg har boutgifter på aktivitetsadressen min",
		"Jeg har fortsatt boutgifter på hjemstedet mitt"
	)
	protected var boutgifterPaAktivitetsadressen: Int = 3500
	protected var boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten: Int = 0
	protected var erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet: String = "Nei"

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

package no.nav.soknad.innsending.utils.builders.tilleggsstonad

import no.nav.soknad.innsending.util.mapping.tilleggsstonad.*
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonBostotteTestBuilder {

	private var fradato: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	private var tildato: String = Date.formatToLocalDate(LocalDateTime.now().plusMonths(3))
	private var hvilkeBoutgifterSokerDuOmAFaDekket: String =
		"boutgifterIForbindelseMedSamling" // alternativt "fasteBoutgifter"
	private var bostotteIForbindelseMedSamling: BostotteIForbindelseMedSamling? = null
	private var mottarDuBostotteFraKommunen: String = "Nei"
	private var hvilkeAdresserHarDuBoutgifterPa: HvilkeAdresserHarDuBoutgifterPa = HvilkeAdresserHarDuBoutgifterPa(
		boutgifterPaAktivitetsadressen = "true",
		boutgifterPaHjemstedet = "true",
		boutgifterPaHjemstedetMittSomHarOpphortIForbindelseMedAktiviteten = "true"
	)
	private var bostottebelop: Int? = 1000
	private var boutgifterPaHjemstedetMitt: Int? = 4000
	private var boutgifterPaAktivitetsadressen: Int? = 3500
	private var boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten: Int? = 0
	private var erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet: String = "Nei"

	fun fradato(fradato: String) = apply { this.fradato = fradato }
	fun tildato(tildato: String) = apply { this.tildato = tildato }
	fun hvilkeBoutgifterSokerDuOmAFaDekket(hvilkeBoutgifterSokerDuOmAFaDekket: String) =
		apply { this.hvilkeBoutgifterSokerDuOmAFaDekket = hvilkeBoutgifterSokerDuOmAFaDekket }

	fun bostotteIForbindelseMedSamling(bostotteIForbindelseMedSamling: BostotteIForbindelseMedSamling?) =
		apply { this.bostotteIForbindelseMedSamling = bostotteIForbindelseMedSamling }

	fun mottarDuBostotteFraKommunen(mottarDuBostotteFraKommunen: String) =
		apply {
			this.mottarDuBostotteFraKommunen = mottarDuBostotteFraKommunen
			if (!"Ja".equals(mottarDuBostotteFraKommunen, true)) this.bostottebelop = null
		}

	fun hvilkeAdresserHarDuBoutgifterPa(hvilkeAdresserHarDuBoutgifterPa: HvilkeAdresserHarDuBoutgifterPa) =
		apply { this.hvilkeAdresserHarDuBoutgifterPa = hvilkeAdresserHarDuBoutgifterPa }

	fun bostottebelop(bostottebelop: Int?) =
		apply { this.bostottebelop = bostottebelop }


	fun boutgifterPaHjemstedetMitt(boutgifterPaHjemstedetMitt: Int?) =
		apply { this.boutgifterPaHjemstedetMitt = boutgifterPaHjemstedetMitt }

	fun boutgifterPaAktivitetsadressen(boutgifterPaAktivitetsadressen: Int) =
		apply { this.boutgifterPaAktivitetsadressen = boutgifterPaAktivitetsadressen }

	fun boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten(
		boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten: Int
	) = apply {
		this.boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten =
			boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten
	}

	fun erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet(
		erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet: String
	) = apply {
		this.erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet =
			erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet
	}

	fun buildBostotteSoknad() = JsonBostottesoknad(
		aktivitetsperiode = JsonPeriode(startdatoDdMmAaaa = fradato, sluttdatoDdMmAaaa = tildato),
		hvilkeBoutgifterSokerDuOmAFaDekket = hvilkeBoutgifterSokerDuOmAFaDekket,
		boutgifterPaHjemstedetMitt = boutgifterPaHjemstedetMitt,
		bostotteIForbindelseMedSamling = bostotteIForbindelseMedSamling,
		mottarDuBostotteFraKommunen = mottarDuBostotteFraKommunen,
		bostottebelop = bostottebelop,
		hvilkeAdresserHarDuBoutgifterPa = hvilkeAdresserHarDuBoutgifterPa,
		boutgifterPaAktivitetsadressen = boutgifterPaAktivitetsadressen,
		boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten = boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten,
		erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet = erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet
	)

	fun build() = JsonRettighetstyper(
		bostotte = buildBostotteSoknad()
	)

}

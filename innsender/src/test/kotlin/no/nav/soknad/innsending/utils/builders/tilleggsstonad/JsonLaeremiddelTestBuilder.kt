package no.nav.soknad.innsending.utils.builders.tilleggsstonad

import no.nav.soknad.innsending.util.mapping.tilleggsstonad.JsonLaeremiddelutgifter
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.JsonPeriode
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.JsonRettighetstyper
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonLaeremiddelTestBuilder {

	private var fradato: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	private var tildato: String = Date.formatToLocalDate(LocalDateTime.now().plusMonths(3))
	private var hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore: String =
		"Jeg skal ta videregående utdanning, eller forkurs på universitet"
	private var hvilketKursEllerAnnenFormForUtdanningSkalDuTa: String? = null
	private var oppgiHvorMangeProsentDuStudererEllerGarPaKurs: Double = 100.0
	private var harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler: String = "Ja"
	private var utgifterTilLaeremidler: Double = 6000.0
	private var farDuDekketLaeremidlerEtterAndreOrdninger: String = "Delvis"
	private var hvorMyeFarDuDekketAvEnAnnenAktor: Double? = 2000.0
	private var hvorStortBelopSokerDuOmAFaDekketAvNav: Double = 4000.0

	fun fradato(fradato: String) = apply { this.fradato = fradato }
	fun tildato(tildato: String) = apply { this.tildato = tildato }
	fun hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore(hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore: String) =
		apply {
			this.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore = hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore
		}

	fun hvilketKursEllerAnnenFormForUtdanningSkalDuTa(hvilketKursEllerAnnenFormForUtdanningSkalDuTa: String?) =
		apply { this.hvilketKursEllerAnnenFormForUtdanningSkalDuTa = hvilketKursEllerAnnenFormForUtdanningSkalDuTa }

	fun oppgiHvorMangeProsentDuStudererEllerGarPaKurs(oppgiHvorMangeProsentDuStudererEllerGarPaKurs: Double) =
		apply { this.oppgiHvorMangeProsentDuStudererEllerGarPaKurs = oppgiHvorMangeProsentDuStudererEllerGarPaKurs }

	fun harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler(
		harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler: String
	) = apply {
		this.harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler =
			harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler
	}

	fun utgifterTilLaeremidler(utgifterTilLaeremidler: Double) =
		apply { this.utgifterTilLaeremidler = utgifterTilLaeremidler }

	fun farDuDekketLaeremidlerEtterAndreOrdninger(farDuDekketLaeremidlerEtterAndreOrdninger: String) =
		apply { this.farDuDekketLaeremidlerEtterAndreOrdninger = farDuDekketLaeremidlerEtterAndreOrdninger }

	fun hvorMyeFarDuDekketAvEnAnnenAktor(hvorMyeFarDuDekketAvEnAnnenAktor: Double?) =
		apply { this.hvorMyeFarDuDekketAvEnAnnenAktor = hvorMyeFarDuDekketAvEnAnnenAktor }

	fun hvorStortBelopSokerDuOmAFaDekketAvNav(hvorStortBelopSokerDuOmAFaDekketAvNav: Double) =
		apply { this.hvorStortBelopSokerDuOmAFaDekketAvNav = hvorStortBelopSokerDuOmAFaDekketAvNav }


	fun buildLaermiddelutgifter() = JsonLaeremiddelutgifter(
		aktivitetsperiode = JsonPeriode(startdatoDdMmAaaa = fradato, sluttdatoDdMmAaaa = tildato),
		hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore = hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore,
		hvilketKursEllerAnnenFormForUtdanningSkalDuTa = hvilketKursEllerAnnenFormForUtdanningSkalDuTa,
		oppgiHvorMangeProsentDuStudererEllerGarPaKurs = oppgiHvorMangeProsentDuStudererEllerGarPaKurs,
		harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler = harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler,
		utgifterTilLaeremidler = utgifterTilLaeremidler,
		farDuDekketLaeremidlerEtterAndreOrdninger = farDuDekketLaeremidlerEtterAndreOrdninger,
		hvorMyeFarDuDekketAvEnAnnenAktor = hvorMyeFarDuDekketAvEnAnnenAktor,
		hvorStortBelopSokerDuOmAFaDekketAvNav = hvorStortBelopSokerDuOmAFaDekketAvNav
	)

	fun build() = JsonRettighetstyper(
		laeremiddelutgifter = buildLaermiddelutgifter()
	)
}

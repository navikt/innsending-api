package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.IkkeRegistrertAktivitetsperiode
import no.nav.soknad.innsending.util.mapping.JsonLaeremiddelutgifter
import no.nav.soknad.innsending.util.mapping.JsonRettighetstyper

class JsonLaeremiddelTestBuilder(
	var fradato: String,
	var tildato: String,
	var hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore: String = "Jeg skal ta videregående utdanning, eller forkurs på universitet",
	var hvilketKursEllerAnnenFormForUtdanningSkalDuTa: String? = null,
	var oppgiHvorMangeProsentDuStudererEllerGarPaKurs: Int = 100,
	var harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler: String = "Ja",
	var utgifterTilLaeremidler: Int = 6000,
	var farDuDekketLaeremidlerEtterAndreOrdninger: String = "Delvis",
	var hvorMyeFarDuDekketAvEnAnnenAktor: Int = 2000,
	var hvorStortBelopSokerDuOmAFaDekketAvNav: Int = 4000
) {

	fun buildLaermiddelutgifter() = JsonLaeremiddelutgifter(
		aktivitetsperiode = IkkeRegistrertAktivitetsperiode(startdatoDdMmAaaa = fradato, sluttdatoDdMmAaaa = tildato),
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

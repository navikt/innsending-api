package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.IkkeRegistrertAktivitetsperiode
import no.nav.soknad.innsending.util.mapping.JsonLaeremiddelutgifter
import no.nav.soknad.innsending.util.mapping.JsonRettighetstyper
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonLaeremiddelTestBuilder {

	protected var fradato: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	protected var tildato: String = Date.formatToLocalDate(LocalDateTime.now().plusMonths(3))
	protected var hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore: String =
		"Jeg skal ta videregående utdanning, eller forkurs på universitet"
	protected var hvilketKursEllerAnnenFormForUtdanningSkalDuTa: String? = null
	protected var oppgiHvorMangeProsentDuStudererEllerGarPaKurs: Int = 100
	protected var harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler: String = "Ja"
	protected var utgifterTilLaeremidler: Int = 6000
	protected var farDuDekketLaeremidlerEtterAndreOrdninger: String = "Delvis"
	protected var hvorMyeFarDuDekketAvEnAnnenAktor: Int = 2000
	protected var hvorStortBelopSokerDuOmAFaDekketAvNav: Int = 4000

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

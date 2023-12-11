package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.*
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonBarnePassTestBuilder {

	protected var fradato: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	protected var tildato: String = Date.formatToLocalDate(LocalDateTime.now().plusMonths(1))
	protected var barnePass: List<BarnePass> = listOf(
		BarnePass(
			fornavn = "Lite",
			etternavn = "Barn",
			fodselsdatoDdMmAaaa = "2020-04-03",
			jegSokerOmStonadTilPassAvDetteBarnet = "Ja",
			sokerStonadForDetteBarnet = SokerStonadForDetteBarnet(
				hvemPasserBarnet = "Barnet mitt får pass av dagmamma eller dagpappa",
				oppgiManedligUtgiftTilBarnepass = 4000,
				harBarnetFullfortFjerdeSkolear = "Nei",
				hvaErArsakenTilAtBarnetDittTrengerPass = null
			)
		)
	)

	fun barnePass(barnePass: List<BarnePass>) = apply { this.barnePass = barnePass }

	fun build() = JsonRettighetstyper(
		tilsynsutgifter = JsonTilsynsutgifter(
			aktivitetsPeriode = AktivitetsPeriode(startdatoDdMmAaaa = fradato, sluttdatoDdMmAaaa = tildato),
			barnePass = barnePass,
			fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa = "1995-03-04"
		)
	)

}

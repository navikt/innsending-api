package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.*

class JsonBarnePassTestBuilder(
	var fradato: String,
	var tildato: String,
	var barnePass: List<BarnePass> = listOf(
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

) {

	fun build() = JsonRettighetstyper(
		tilsynsutgifter = JsonTilsynsutgifter(
			aktivitetsPeriode = AktivitetsPeriode(startdatoDdMmAaaa = fradato, sluttdatoDdMmAaaa = tildato),
			barnePass = barnePass,
			fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa = "1995-03-04"
		)
	)

}

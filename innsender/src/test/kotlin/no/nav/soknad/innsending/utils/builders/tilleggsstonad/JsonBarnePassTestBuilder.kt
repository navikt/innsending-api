package no.nav.soknad.innsending.utils.builders.tilleggsstonad

import no.nav.soknad.innsending.util.mapping.tilleggsstonad.*
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonBarnePassTestBuilder {

	private var fradato: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	private var tildato: String = Date.formatToLocalDate(LocalDateTime.now().plusMonths(1))
	private var fnrBarn: String = "01902399964"
	private var barnePass: List<BarnePass> = listOf(
		BarnePass(
			fornavn = "Lite",
			etternavn = "Barn",
			fodselsdatoDdMmAaaa = fnrBarn,
			jegSokerOmStonadTilPassAvDetteBarnet = true,
			sokerStonadForDetteBarnet = SokerStonadForDetteBarnet(
				hvemPasserBarnet = "Barnet mitt får pass av dagmamma eller dagpappa",
				oppgiManedligUtgiftTilBarnepass = 4000,
				harBarnetFullfortFjerdeSkolear = "Nei",
				hvaErArsakenTilAtBarnetDittTrengerPass = null
			)
		)
	)
	private var fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa: String? = "1995-03-04"
	private var fodselsnummerDNummerAndreForelder: String? = "05844198215"

	fun barnePass(barnePass: List<BarnePass>) = apply { this.barnePass = barnePass }
	fun fradato(fradato: String) = apply { this.fradato = fradato }
	fun tildato(tildato: String) = apply { this.tildato = tildato }
	fun fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa(fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa: String?) =
		apply { this.fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa = fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa }

	fun fodselsnummerDNummerAndreForelder(fodselsnummerDNummerAndreForelder: String?) =
		apply { this.fodselsnummerDNummerAndreForelder = fodselsnummerDNummerAndreForelder }

	fun build() = JsonRettighetstyper(
		tilsynsutgifter = JsonTilsynsutgifter(
			aktivitetsPeriode = JsonPeriode(startdatoDdMmAaaa = fradato, sluttdatoDdMmAaaa = tildato),
			barnePass = barnePass,
			fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa = fodselsnummerDNummerAndreForelder
				?: fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa,
		)
	)

}

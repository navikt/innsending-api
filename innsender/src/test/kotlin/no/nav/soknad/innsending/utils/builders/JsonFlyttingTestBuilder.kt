package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.*

class JsonFlyttingTestBuilder(
	var fradato: String,
	var tildato: String,
	var narFlytterDuDdMmAaaa: String,
	var oppgiForsteDagINyJobbDdMmAaaa: String?,
	var hvorforFlytterDu: String = "Jeg flytter fordi jeg har fått ny jobb",
	var farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav: String = "Nei",
	var ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra: String = "Jeg flytter selv",
	var jegFlytterSelv: JegFlytterSelv = JegFlytterSelv(
		hvorLangtSkalDuFlytte = 130, hengerleie = 1000, bom = null, parkering = 200, ferje = 0, annet = null
	),
	var jegVilBrukeFlyttebyra: JegVilBrukeFlyttebyra? = null,
	var jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv: JegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv? = null
) {

	fun buildFlytteutgifter() = JsonFlytteutgifter(
		aktivitetsperiode = IkkeRegistrertAktivitetsperiode(startdatoDdMmAaaa = fradato, sluttdatoDdMmAaaa = tildato),
		hvorforFlytterDu = hvorforFlytterDu,
		narFlytterDuDdMmAaaa = narFlytterDuDdMmAaaa,
		oppgiForsteDagINyJobbDdMmAaaa = oppgiForsteDagINyJobbDdMmAaaa,
		farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav = farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav,
		ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra = ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra,
		jegFlytterSelv = jegFlytterSelv,
		jegVilBrukeFlyttebyra = jegVilBrukeFlyttebyra,
		jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv = jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv
	)

	fun build() = JsonRettighetstyper(
		flytteutgifter = buildFlytteutgifter()
	)

}

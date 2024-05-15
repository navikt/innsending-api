package no.nav.soknad.innsending.utils.builders.tilleggsstonad

import no.nav.soknad.innsending.util.mapping.tilleggsstonad.*
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonFlyttingTestBuilder {

	fun buildFlytteutgifter() = JsonFlytteutgifter(
		aktivitetsperiode = JsonPeriode(startdatoDdMmAaaa = fradato, sluttdatoDdMmAaaa = tildato),
		hvorforFlytterDu = hvorforFlytterDu,
		narFlytterDuDdMmAaaa = narFlytterDuDdMmAaaa,
		oppgiForsteDagINyJobbDdMmAaaa = oppgiForsteDagINyJobbDdMmAaaa,
		erBostedEtterFlytting = erBostedEtterFlytting,
		velgLand1 = velgLand1,
		adresse1 = adresse1,
		postnr1 = postnr1,
		farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav = farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav,
		ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra = ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra,
		jegFlytterSelv = jegFlytterSelv,
		jegVilBrukeFlyttebyra = jegVilBrukeFlyttebyra,
		jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv = jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv
	)

	private var fradato: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	private var tildato: String = Date.formatToLocalDate(LocalDateTime.now().plusMonths(3))
	private var narFlytterDuDdMmAaaa: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	private var oppgiForsteDagINyJobbDdMmAaaa: String? = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	private var erBostedEtterFlytting: Boolean = true
	private var velgLand1: VelgLand = VelgLand(label = "Norge", value = "NO")
	private var adresse1: String = "Kongensgate 10"
	private var postnr1: String = "3701"
	private var hvorforFlytterDu: String = "Jeg flytter fordi jeg har fått ny jobb"
	private var farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav: String = "Nei"
	private var ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra: String = "jegFlytterSelv"
	private var jegFlytterSelv: JegFlytterSelv? = JegFlytterSelv(
		hvorLangtSkalDuFlytte = 130.5, hengerleie = 1000, bom = null, parkering = 200, ferje = 0, annet = null
	)
	private var jegVilBrukeFlyttebyra: JegVilBrukeFlyttebyra? = null
	private var jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv: JegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv? =
		null

	fun fradato(fradato: String) = apply { this.fradato = fradato }
	fun fradatoOgtildato(fradato: String, tildato: String) = apply { this.fradato = fradato; this.tildato = tildato }
	fun tildato(tildato: String) = apply { this.tildato = tildato }
	fun narFlytterDuDdMmAaaa(narFlytterDuDdMmAaaa: String) = apply { this.narFlytterDuDdMmAaaa = narFlytterDuDdMmAaaa }
	fun oppgiForsteDagINyJobbDdMmAaaa(oppgiForsteDagINyJobbDdMmAaaa: String?) =
		apply { this.oppgiForsteDagINyJobbDdMmAaaa = oppgiForsteDagINyJobbDdMmAaaa }

	fun erBostedEtterFlytting(erBostedEtterFlytting: Boolean?) =
		apply { this.erBostedEtterFlytting = erBostedEtterFlytting ?: false }

	fun velgLand1(velgLand1: VelgLand) = apply { this.velgLand1 = velgLand1 }
	fun adresse1(adresse1: String) = apply { this.adresse1 = adresse1 }
	fun postnr1(postnr1: String) = apply { this.postnr1 = postnr1 }
	fun hvorforFlytterDu(hvorforFlytterDu: String) = apply { this.hvorforFlytterDu = hvorforFlytterDu }
	fun farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav(
		farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav: String
	) = apply {
		this.farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav =
			farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav
	}

	fun ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra(ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra: String) =

		apply {
			this.ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra =
				ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra
		}

	fun jegFlytterSelv(jegFlytterSelv: JegFlytterSelv?) = apply { this.jegFlytterSelv = jegFlytterSelv }
	fun jegVilBrukeFlyttebyra(jegVilBrukeFlyttebyra: JegVilBrukeFlyttebyra?) =
		apply { this.jegVilBrukeFlyttebyra = jegVilBrukeFlyttebyra  }

	fun jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv(
		jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv: JegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv?
	) = apply {
		this.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv =
			jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv
	}


	fun build() = JsonRettighetstyper(
		flytteutgifter = buildFlytteutgifter()
	)

}

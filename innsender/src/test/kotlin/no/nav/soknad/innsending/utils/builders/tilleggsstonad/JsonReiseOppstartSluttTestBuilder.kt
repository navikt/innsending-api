package no.nav.soknad.innsending.utils.builders.tilleggsstonad

import no.nav.soknad.innsending.util.mapping.tilleggsstonad.*
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonReiseOppstartSluttTestBuilder {

	private var startdatoDdMmAaaa1: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	private var sluttdatoDdMmAaaa1: String = Date.formatToLocalDate(LocalDateTime.now().plusMonths(1))
	private var soknadsperiode1: SoknadsPeriode =
		SoknadsPeriode(startdato = startdatoDdMmAaaa1, sluttdato = sluttdatoDdMmAaaa1)
	private var hvorLangReiseveiHarDu2: Int = 100
	private var hvorMangeGangerSkalDuReiseEnVei: Int = 4
	private var velgLand3: VelgLand = VelgLand(label = "Norge", value = "NO")
	private var adresse3: String = "Kongensgate 10"
	private var postnr3: String? = "3701"
	private var harDuBarnSomSkalFlytteMedDeg: String = "Ja"
	private var barnSomSkalFlytteMedDeg: List<BarnSomSkalFlytteMedDeg>? = listOf(
		BarnSomSkalFlytteMedDeg(
			fornavn = "Lite",
			etternavn = "Barn",
			fodselsdatoDdMmAaaa = "2020-03-03"
		)
	)
	private var harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear: String? = "Ja"
	private var harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor: String = "Ja"
	private var bekreftelseForBehovForFlereHjemreiser1: String? = "ddsfladnk"
	private var kanDuReiseKollektivtOppstartAvslutningHjemreise: String = "Ja"
	private var hvilkeUtgifterHarDuIForbindelseMedReisen4: Int? = 3000
	private var kanIkkeReiseKollektivtOppstartAvslutningHjemreise: KanIkkeReiseKollektivt? =
		null

	fun startdatoDdMmAaaa1(startdatoDdMmAaaa1: String) = apply { this.startdatoDdMmAaaa1 = startdatoDdMmAaaa1 }
	fun sluttdatoDdMmAaaa1(sluttdatoDdMmAaaa1: String) = apply { this.sluttdatoDdMmAaaa1 = sluttdatoDdMmAaaa1 }

	fun soknadsPeriode(startdato: String, sluttdato: String) = apply {
		this.soknadsperiode1 = SoknadsPeriode(startdato = startdato, sluttdato = sluttdato)
	}

	fun hvorLangReiseveiHarDu2(hvorLangReiseveiHarDu2: Int) =
		apply { this.hvorLangReiseveiHarDu2 = hvorLangReiseveiHarDu2 }

	fun hvorMangeGangerSkalDuReiseEnVei(hvorMangeGangerSkalDuReiseEnVei: Int) =
		apply { this.hvorMangeGangerSkalDuReiseEnVei = hvorMangeGangerSkalDuReiseEnVei }

	fun velgLand3(velgLand3: VelgLand) = apply { this.velgLand3 = velgLand3 }
	fun adresse3(adresse3: String) = apply { this.adresse3 = adresse3 }
	fun postnr3(postnr3: String?) = apply { this.postnr3 = postnr3 }
	fun harDuBarnSomSkalFlytteMedDeg(harDuBarnSomSkalFlytteMedDeg: String) =
		apply { this.harDuBarnSomSkalFlytteMedDeg = harDuBarnSomSkalFlytteMedDeg }

	fun barnSomSkalFlytteMedDeg(barnSomSkalFlytteMedDeg: List<BarnSomSkalFlytteMedDeg>?) =
		apply { this.barnSomSkalFlytteMedDeg = barnSomSkalFlytteMedDeg }

	fun harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear(harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear: String?) =
		apply {
			this.harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear =
				harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear
		}

	fun harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor(harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor: String) =
		apply {
			this.harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor = harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor
		}

	fun bekreftelseForBehovForFlereHjemreiser1(bekreftelseForBehovForFlereHjemreiser1: String?) =
		apply { this.bekreftelseForBehovForFlereHjemreiser1 = bekreftelseForBehovForFlereHjemreiser1 }

	fun kanDuReiseKollektivtOppstartAvslutningHjemreise(kanDuReiseKollektivtOppstartAvslutningHjemreise: String) =
		apply {
			this.kanDuReiseKollektivtOppstartAvslutningHjemreise = kanDuReiseKollektivtOppstartAvslutningHjemreise
			if (!"Ja".equals(kanDuReiseKollektivtOppstartAvslutningHjemreise)) hvilkeUtgifterHarDuIForbindelseMedReisen4 =
				null
		}

	fun hvilkeUtgifterHarDuIForbindelseMedReisen4(hvilkeUtgifterHarDuIForbindelseMedReisen4: Int?) =
		apply { this.hvilkeUtgifterHarDuIForbindelseMedReisen4 = hvilkeUtgifterHarDuIForbindelseMedReisen4 }

	fun kanIkkeReiseKollektivtOppstartAvslutningHjemreise(kanIkkeReiseKollektivtOppstartAvslutningHjemreise: KanIkkeReiseKollektivt?) =
		apply { this.kanIkkeReiseKollektivtOppstartAvslutningHjemreise = kanIkkeReiseKollektivtOppstartAvslutningHjemreise }


	fun build() = JsonOppstartOgAvsluttetAktivitet(
		startdatoDdMmAaaa1 = startdatoDdMmAaaa1,
		sluttdatoDdMmAaaa1 = sluttdatoDdMmAaaa1,
		hvorLangReiseveiHarDu2 = hvorLangReiseveiHarDu2,
		hvorMangeGangerSkalDuReiseEnVei = hvorMangeGangerSkalDuReiseEnVei,
		velgLand3 = velgLand3,
		adresse3 = adresse3,
		postnr3 = postnr3,
		harDuBarnSomSkalFlytteMedDeg = harDuBarnSomSkalFlytteMedDeg,
		barnSomSkalFlytteMedDeg = barnSomSkalFlytteMedDeg,
		harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear = harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear,
		harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor = harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor,
		bekreftelseForBehovForFlereHjemreiser1 = bekreftelseForBehovForFlereHjemreiser1,
		kanDuReiseKollektivtOppstartAvslutningHjemreise = kanDuReiseKollektivtOppstartAvslutningHjemreise,
		hvilkeUtgifterHarDuIForbindelseMedReisen4 = hvilkeUtgifterHarDuIForbindelseMedReisen4,
		kanIkkeReiseKollektivtOppstartAvslutningHjemreise = kanIkkeReiseKollektivtOppstartAvslutningHjemreise
	)

}

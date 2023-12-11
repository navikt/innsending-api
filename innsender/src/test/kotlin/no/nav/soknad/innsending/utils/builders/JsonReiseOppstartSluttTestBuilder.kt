package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.BarnSomSkalFlytteMedDeg
import no.nav.soknad.innsending.util.mapping.JsonOppstartOgAvsluttetAktivitet
import no.nav.soknad.innsending.util.mapping.KanIkkeReiseKollektivtOppstartAvslutningHjemreise
import no.nav.soknad.innsending.util.mapping.VelgLand3
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonReiseOppstartSluttTestBuilder {

	protected var startdatoDdMmAaaa1: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	protected var sluttdatoDdMmAaaa1: String = Date.formatToLocalDate(LocalDateTime.now().plusMonths(1))
	protected var hvorLangReiseveiHarDu2: Int = 100
	protected var hvorMangeGangerSkalDuReiseEnVei: Int = 4
	protected var velgLand3: VelgLand3 = VelgLand3(label = "NO", value = "Norge")
	protected var adresse3: String = "Kongensgate 10"
	protected var postnr3: String = "3701"
	protected var harDuBarnSomSkalFlytteMedDeg: String = "Ja"
	protected var barnSomSkalFlytteMedDeg: List<BarnSomSkalFlytteMedDeg>? = listOf(
		BarnSomSkalFlytteMedDeg(
			fornavn = "Lite",
			etternavn = "Barn",
			fodselsdatoDdMmAaaa = "2020-03-03"
		)
	)
	protected var harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear: String? = "Ja"
	protected var harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor: String = "Ja"
	protected var bekreftelseForBehovForFlereHjemreiser1: String? = "ddsfladnk"
	protected var kanDuReiseKollektivtOppstartAvslutningHjemreise: String = "Ja"
	protected var hvilkeUtgifterHarDuIForbindelseMedReisen4: Int? = 3000
	protected var kanIkkeReiseKollektivtOppstartAvslutningHjemreise: KanIkkeReiseKollektivtOppstartAvslutningHjemreise? =
		null

	fun startdatoDdMmAaaa1(startdatoDdMmAaaa1: String) = apply { this.startdatoDdMmAaaa1 = startdatoDdMmAaaa1 }
	fun sluttdatoDdMmAaaa1(sluttdatoDdMmAaaa1: String) = apply { this.sluttdatoDdMmAaaa1 = sluttdatoDdMmAaaa1 }
	fun hvorLangReiseveiHarDu2(hvorLangReiseveiHarDu2: Int) =
		apply { this.hvorLangReiseveiHarDu2 = hvorLangReiseveiHarDu2 }

	fun hvorMangeGangerSkalDuReiseEnVei(hvorMangeGangerSkalDuReiseEnVei: Int) =
		apply { this.hvorMangeGangerSkalDuReiseEnVei = hvorMangeGangerSkalDuReiseEnVei }

	fun velgLand3(velgLand3: VelgLand3) = apply { this.velgLand3 = velgLand3 }
	fun adresse3(adresse3: String) = apply { this.adresse3 = adresse3 }
	fun postnr3(postnr3: String) = apply { this.postnr3 = postnr3 }
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
		apply { this.kanDuReiseKollektivtOppstartAvslutningHjemreise = kanDuReiseKollektivtOppstartAvslutningHjemreise }

	fun hvilkeUtgifterHarDuIForbindelseMedReisen4(hvilkeUtgifterHarDuIForbindelseMedReisen4: Int?) =
		apply { this.hvilkeUtgifterHarDuIForbindelseMedReisen4 = hvilkeUtgifterHarDuIForbindelseMedReisen4 }

	fun kanIkkeReiseKollektivtOppstartAvslutningHjemreise(kanIkkeReiseKollektivtOppstartAvslutningHjemreise: KanIkkeReiseKollektivtOppstartAvslutningHjemreise?) =
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

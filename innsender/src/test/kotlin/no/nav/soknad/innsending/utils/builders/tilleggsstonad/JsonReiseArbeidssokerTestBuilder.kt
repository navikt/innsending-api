package no.nav.soknad.innsending.utils.builders.tilleggsstonad

import no.nav.soknad.innsending.util.mapping.tilleggsstonad.HarMottattDagpengerSiste6Maneder
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.JsonDagligReiseArbeidssoker
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.KanIkkeReiseKollektivt
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.VelgLand
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonReiseArbeidssokerTestBuilder {

	private var reisedatoDdMmAaaa: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	private var hvorforReiserDuArbeidssoker: String = "oppfolgingFraNav"
	private var dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis: String = "Nei"
	private var mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene: String = "Nei"
	private var harMottattDagpengerSiste6Maneder: HarMottattDagpengerSiste6Maneder? = null
	private var hvorLangReiseveiHarDu3: Double = 120.0
	private var velgLandArbeidssoker: VelgLand = VelgLand(label = "Norge", value = "NO")
	private var adresse: String = "Kongensgate 10"
	private var postnr: String = "3701"
	private var kanDuReiseKollektivtArbeidssoker: String = "Ja"
	private var hvilkeUtgifterHarDuIForbindelseMedReisen3: Double? = 5000.0
	private var kanIkkeReiseKollektivtArbeidssoker: KanIkkeReiseKollektivt? = null

	fun reisedatoDdMmAaaa(reisedatoDdMmAaaa: String) = apply { this.reisedatoDdMmAaaa = reisedatoDdMmAaaa }
	fun hvorforReiserDuArbeidssoker(hvorforReiserDuArbeidssoker: String) =
		apply { this.hvorforReiserDuArbeidssoker = hvorforReiserDuArbeidssoker }

	fun dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis(dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis: String) =
		apply {
			this.dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis = dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis
		}

	fun mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene(
		mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene: String
	) = apply {
		this.mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene =
			mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene
	}

	fun harMottattDagpengerSiste6Maneder(harMottattDagpengerSiste6Maneder: HarMottattDagpengerSiste6Maneder) =
		apply { this.harMottattDagpengerSiste6Maneder = harMottattDagpengerSiste6Maneder }

	fun hvorLangReiseveiHarDu3(hvorLangReiseveiHarDu3: Double) =
		apply { this.hvorLangReiseveiHarDu3 = hvorLangReiseveiHarDu3 }

	fun velgLandArbeidssoker(velgLandArbeidssoker: VelgLand) = apply { this.velgLandArbeidssoker = velgLandArbeidssoker }
	fun adresse(adresse: String) = apply { this.adresse = adresse }
	fun postnr(postnr: String) = apply { this.postnr = postnr }
	fun kanDuReiseKollektivtArbeidssoker(kanDuReiseKollektivtArbeidssoker: String) =
		apply { this.kanDuReiseKollektivtArbeidssoker = kanDuReiseKollektivtArbeidssoker }

	fun hvilkeUtgifterHarDuIForbindelseMedReisen3(hvilkeUtgifterHarDuIForbindelseMedReisen3: Double) =
		apply { this.hvilkeUtgifterHarDuIForbindelseMedReisen3 = hvilkeUtgifterHarDuIForbindelseMedReisen3 }

	fun kanIkkeReiseKollektivtArbeidssoker(kanIkkeReiseKollektivtArbeidssoker: KanIkkeReiseKollektivt) =
		apply { this.kanIkkeReiseKollektivtArbeidssoker = kanIkkeReiseKollektivtArbeidssoker }


	fun build() = JsonDagligReiseArbeidssoker(
		reisedatoDdMmAaaa = reisedatoDdMmAaaa,
		hvorforReiserDuArbeidssoker = hvorforReiserDuArbeidssoker,
		dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis = dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis,
		mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene = mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene,
		harMottattDagpengerSiste6Maneder = harMottattDagpengerSiste6Maneder,
		hvorLangReiseveiHarDu3 = hvorLangReiseveiHarDu3,
		velgLandArbeidssoker = velgLandArbeidssoker,
		adresse = adresse,
		postnr = postnr,
		kanDuReiseKollektivtArbeidssoker = kanDuReiseKollektivtArbeidssoker,
		hvilkeUtgifterHarDuIForbindelseMedReisen3 = hvilkeUtgifterHarDuIForbindelseMedReisen3,
		kanIkkeReiseKollektivtArbeidssoker = kanIkkeReiseKollektivtArbeidssoker
	)

}

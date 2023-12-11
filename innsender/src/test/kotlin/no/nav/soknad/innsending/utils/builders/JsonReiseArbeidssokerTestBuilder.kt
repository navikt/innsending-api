package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.HarMottattDagpengerSiste6Maneder
import no.nav.soknad.innsending.util.mapping.JsonDagligReiseArbeidssoker
import no.nav.soknad.innsending.util.mapping.KanIkkeReiseKollektivtArbeidssoker
import no.nav.soknad.innsending.util.mapping.VelgLandArbeidssoker
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonReiseArbeidssokerTestBuilder {

	protected var reisedatoDdMmAaaa: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	protected var hvorforReiserDuArbeidssoker: String = "oppfolgingFraNav"
	protected var dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis: String = "Nei"
	protected var mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene: String = "Nei"
	protected var harMottattDagpengerSiste6Maneder: HarMottattDagpengerSiste6Maneder? = null
	protected var hvorLangReiseveiHarDu3: Int = 120
	protected var velgLandArbeidssoker: VelgLandArbeidssoker = VelgLandArbeidssoker(label = "Norge", value = "NO")
	protected var adresse: String = "Kongensgate 10"
	protected var postnr: String = "3701"
	protected var kanDuReiseKollektivtArbeidssoker: String = "Ja"
	protected var hvilkeUtgifterHarDuIForbindelseMedReisen3: Int? = 5000
	protected var kanIkkeReiseKollektivtArbeidssoker: KanIkkeReiseKollektivtArbeidssoker? = null

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

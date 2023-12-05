package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.*


class JsonReiseTestBuilder(
	var dagligReise: JsonDagligReise? = null,
	var samling: JsonReiseSamling? = null,
	var startAvslutning: JsonOppstartOgAvsluttetAktivitet? = null,
	var reiseArbeidssoker: JsonDagligReiseArbeidssoker? = null

) {
	fun build() = JsonRettighetstyper(
		reise = JsonReisestottesoknad(
			dagligReise = dagligReise,
			reiseSamling = samling,
			dagligReiseArbeidssoker = reiseArbeidssoker,
			oppstartOgAvsluttetAktivitet = startAvslutning,
			hvorforReiserDu = HvorforReiserDu(
				dagligReise = dagligReise != null,
				reiseTilSamling = samling != null,
				reisePaGrunnAvOppstartAvslutningEllerHjemreise = startAvslutning != null,
				reiseNarDuErArbeidssoker = reiseArbeidssoker != null
			)
		)
	)

}

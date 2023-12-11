package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.*


class JsonReiseTestBuilder {

	private var dagligReise: JsonDagligReise? = null
	private var samling: JsonReiseSamling? = null
	private var startAvslutning: JsonOppstartOgAvsluttetAktivitet? = null
	private var reiseArbeidssoker: JsonDagligReiseArbeidssoker? = null

	fun dagligReise(dagligReise: JsonDagligReise?) = apply { this.dagligReise = dagligReise }
	fun samling(samling: JsonReiseSamling?) = apply { this.samling = samling }
	fun startAvslutning(startAvslutning: JsonOppstartOgAvsluttetAktivitet?) =
		apply { this.startAvslutning = startAvslutning }

	fun reiseArbeidssoker(reiseArbeidssoker: JsonDagligReiseArbeidssoker?) =
		apply { this.reiseArbeidssoker = reiseArbeidssoker }

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

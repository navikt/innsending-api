package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.util.mapping.HvorforReiserDu
import no.nav.soknad.innsending.util.mapping.JsonDagligReise
import no.nav.soknad.innsending.util.mapping.Metadata

class JsonApplicationTestBuilder(
	var soknadDto: DokumentSoknadDto,
	var metadata: Metadata = Metadata(
		"Europe/Oslo", 60, "https://fyllut-preprod.intern.dev.nav.no", "https://testid.test.idporten.no/", "Netscape",
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
		"/fyllut/nav111212reise/page11", true
	),
	var language: String = "nb-NO",
	var hvorforReiserDu: HvorforReiserDu = HvorforReiserDu(
		dagligReise = true,
		reiseTilSamling = false,
		reisePaGrunnAvOppstartAvslutningEllerHjemreise = false,
		reiseNarDuErArbeidssoker = false
	),
	var dagligReise: JsonDagligReise? = null
) {

	/*
		fun build() = Root(
			language = soknadDto.spraak!!,
			data = ApplicationInfo(
				metadata = metadata,
				state = "submitted",
				vnote = "",
				data = Application(
					fornavnSoker = "Ola",
					etternavnSoker = "Nordmann",
					harDuNorskFodselsnummerEllerDnummer = "Ja",
					fodselsnummerDnummerSoker = soknadDto.brukerId,
					annenDokumentasjon = "",
					harDuNoenTilleggsopplysningerDuMenerErViktigeForSoknadenDin = "",
					hvorforReiserDu = hvorforReiserDu,
					dagligReise = dagligReise,


					)
			)
		)
	*/
}

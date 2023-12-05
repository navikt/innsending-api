package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.*

class JsonApplicationTestBuilder(
	var metadata: Metadata = Metadata(
		"Europe/Oslo", 60, "https://fyllut-preprod.intern.dev.nav.no", "https://testid.test.idporten.no/", "Netscape",
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
		"/fyllut/nav111212reise/page11", true
	),
	var aktivitet: String = "1234",
	var malgruppeInformasjon: JsonMaalgruppeinformasjon = JsonMaalgruppeinformasjon(
		periode = AktivitetsPeriode(startdatoDdMmAaaa = "01-01-2024", sluttdatoDdMmAaaa = "30-06-2024"),
		kilde = "BRUKERREGISTRERT",
		maalgruppetype = JsonMaalgruppetyper(value = "ENSFORARBS")
	),
	var language: String = "nb-NO",
	var rettighetstyper: JsonRettighetstyper
) {
	fun buildJsonTilleggssoknad() = JsonTilleggsstonad(
		aktivitetsinformasjon = JsonAktivitetsInformasjon(aktivitet = aktivitet),
		maalgruppeinformasjon = malgruppeInformasjon,
		rettighetstype = rettighetstyper
	)

	fun build() =
		JsonApplication(
			personInfo = JsonPersonInfo(fornavn = "Test", etternavn = "Testesen", PersonIdent(ident = "12345678901")),
			language = language,
			timezone = metadata.timezone,
			tilleggsstonad = buildJsonTilleggssoknad()
		)

}

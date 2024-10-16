package no.nav.soknad.innsending.utils.builders.tilleggsstonad

import no.nav.soknad.innsending.util.mapping.tilleggsstonad.*

class JsonApplicationTestBuilder {
	fun buildJsonTilleggssoknad() = JsonTilleggsstonad(
		aktivitetsinformasjon = JsonAktivitetsInformasjon(aktivitet = aktivitet),
		maalgruppeinformasjon = malgruppeInformasjon,
		rettighetstype = rettighetstyper
	)

	private var metadata: Metadata = Metadata(
		"Europe/Oslo", 60, "https://fyllut-preprod.intern.dev.nav.no", "https://testid.test.idporten.no/", "Netscape",
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
		"/fyllut/nav111212reise/page11", true
	)
	private var aktivitet: String = "1234"
	private var malgruppeInformasjon: JsonMaalgruppeinformasjon = JsonMaalgruppeinformasjon(
		periode = AktivitetsPeriode(startdatoDdMmAaaa = "01-01-2024", sluttdatoDdMmAaaa = "30-06-2024"),
		kilde = "BRUKERREGISTRERT",
		maalgruppetype = "ENSFORARBS"
	)
	private var language: String = "nb-NO"
	private var rettighetstyper: JsonRettighetstyper? = null

	fun language(language: String) = apply { this.language = language }
	fun aktivitet(aktivitet: String) = apply { this.aktivitet = aktivitet }
	fun malgruppeInformasjon(malgruppeInformasjon: JsonMaalgruppeinformasjon) =
		apply { this.malgruppeInformasjon = malgruppeInformasjon }

	fun rettighetstyper(rettighetstype: JsonRettighetstyper) = apply { this.rettighetstyper = rettighetstype }

	fun build() =
		JsonApplication(
			language = language,
			timezone = metadata.timezone,
			applicationDetails = buildJsonTilleggssoknad()
		)

}

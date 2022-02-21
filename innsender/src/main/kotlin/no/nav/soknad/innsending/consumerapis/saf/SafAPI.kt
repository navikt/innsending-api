package no.nav.soknad.innsending.consumerapis.saf

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker
import no.nav.soknad.innsending.consumerapis.saf.dto.Dokument
import no.nav.soknad.innsending.service.Utilities
import no.nav.soknad.innsending.util.testpersonid
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
@Profile("dev | prod")
@Qualifier("saf")
class SafAPI(private val restConfig: RestConfig): SafInterface, HealthRequestInterface {

	override fun ping(): String {
//		healthApi.ping()
		return "pong"
	}
	override fun isReady(): String {
//		healthApi.isReady()
		return "ok"
	}

	override fun isAlive(): String {
//		healthApi.isReady()
		return "ok"
	}

	override fun hentBrukersSakerIArkivet(brukerId: String): List<ArkiverteSaker>? {
		return dummyArkiverteSoknader.get(brukerId) // TODO erstatt med kall til SAF
	}

	private val date = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format( LocalDateTime.now())

	private val dummyArkiverteSoknader = mapOf (
		testpersonid to listOf (
			ArkiverteSaker(Utilities.laginnsendingsId(), "Test søknad", "BID", date
			, listOf(
				Dokument("NAV 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
				Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
			)),
			ArkiverteSaker(Utilities.laginnsendingsId(), "Ettersending til test søknad", "BID", date
				, listOf(
					Dokument("NAVe 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
					Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
				))
		),
		"12345678902" to listOf(ArkiverteSaker(Utilities.laginnsendingsId(), "Test søknad", "BID", date
			, listOf(
				Dokument("NAV 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
				Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
			))),
		"12345678903" to listOf(ArkiverteSaker(Utilities.laginnsendingsId(), "Test søknad", "BID", date
			, listOf(
				Dokument("NAV 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
				Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
			)))
	)
}

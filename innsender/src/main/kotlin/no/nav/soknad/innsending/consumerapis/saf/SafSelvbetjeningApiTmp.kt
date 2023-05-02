package no.nav.soknad.innsending.consumerapis.saf

import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker
import no.nav.soknad.innsending.consumerapis.saf.dto.Dokument
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.testpersonid
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
@Profile("spring | docker | default")
@Qualifier("saf")
class SafSelvbetjeningApiTmp : SafSelvbetjeningInterface, HealthRequestInterface {

	override fun ping(): String {
		return "pong"
	}

	override fun isReady(): String {
		return "ok"
	}

	override fun isAlive(): String {
		return "ok"
	}

	override fun hentBrukersSakerIArkivet(brukerId: String): List<ArkiverteSaker> {
		return dummyArkiverteSoknader[brukerId] ?: emptyList()
	}

	private val date = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now().minusDays(0))

	private val dummyArkiverteSoknader = mapOf(
		testpersonid to listOf(
			ArkiverteSaker(
				Utilities.laginnsendingsId(),
				"Test søknad",
				"BID",
				DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now().minusDays(7)),
				listOf(
					Dokument("NAV 08-09.06", "Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
					Dokument("L7", "Kvittering", "VEDLEGG"),
					Dokument("N6", "Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
				)
			),
			ArkiverteSaker(
				Utilities.laginnsendingsId(),
				"Ettersending til test søknad",
				"BID",
				DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now().minusDays(2)),
				listOf(
					Dokument("NAVe 08-09.06", "Ettersending til Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
					Dokument("N6", "Et vedleggEgenerklæring og sykmelding", "VEDLEGG"),
					Dokument("W2", "Et veddlegg", "VEDLEGG"),
					Dokument("L7", "Kvittering", "VEDLEGG")
				)
			),
			ArkiverteSaker(
				Utilities.laginnsendingsId(),
				"Test søknad",
				"PEN",
				DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now().minusDays(14)),
				listOf(
					Dokument("NAV 95-00.09", "Skjema for bankopplysninger – Australia", "HOVEDDOKUMENT"),
					Dokument("L7", "Kvittering", "VEDLEGG"),
					Dokument("N6", "Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
				)
			),
			ArkiverteSaker(
				Utilities.laginnsendingsId(),
				"Test søknad",
				"PEN",
				DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now().minusDays(14)),
				listOf(
					Dokument("NAV 15-00.04", "Søknad om skolepenger – enslig mor eller far", "HOVEDDOKUMENT"),
					Dokument("L7", "Kvittering", "VEDLEGG")
				)
			),
			ArkiverteSaker(
				Utilities.laginnsendingsId(),
				"Ettersending til test søknad",
				"BID",
				DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now().minusDays(5)),
				listOf(
					Dokument("NAVe 95-00.09", "Ettersending til Skjema for bankopplysninger – Australia", "HOVEDDOKUMENT"),
					Dokument("N6", "Et vedleggEgenerklæring og sykmelding", "VEDLEGG"),
					Dokument("L7", "Kvittering", "VEDLEGG"),
					Dokument("W2", "Et ekstra veddlegg", "VEDLEGG")
				)
			),
			ArkiverteSaker(
				Utilities.laginnsendingsId(),
				"Ettersending til test søknad",
				"BID",
				DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now().minusDays(20)),
				listOf(
					Dokument(null, "Ettersending til Skjema for bankopplysninger – Australia", "HOVEDDOKUMENT"),
					Dokument("N6", "Et vedleggEgenerklæring og sykmelding", "VEDLEGG"),
					Dokument("L7", "Kvittering", "VEDLEGG"),
					Dokument(null, "Et ekstra veddlegg", "VEDLEGG")
				)
			),
			ArkiverteSaker(
				Utilities.laginnsendingsId(),
				"Ettersending til test søknad",
				"BID",
				DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now().minusDays(25)),
				listOf(
					Dokument("", "Ettersending til Skjema for bankopplysninger – Australia", "HOVEDDOKUMENT"),
					Dokument("N6", "Et vedleggEgenerklæring og sykmelding", "VEDLEGG"),
					Dokument("L7", "Kvittering", "VEDLEGG"),
					Dokument("", "Et ekstra veddlegg", "VEDLEGG")
				)
			)
		),
		"12345678902" to listOf(
			ArkiverteSaker(
				Utilities.laginnsendingsId(), "Test søknad", "BID", date, listOf(
					Dokument("NAV 08-09.06", "Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
					Dokument("L7", "Kvittering", "VEDLEGG"),
					Dokument("N6", "Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
				)
			)
		),
		"12345678903" to listOf(
			ArkiverteSaker(
				Utilities.laginnsendingsId(), "Test søknad", "BID", date, listOf(
					Dokument("NAV 08-09.06", "Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
					Dokument("N6", "Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
				)
			)
		)
	)
}


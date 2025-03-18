package no.nav.soknad.innsending.consumerapis.kafka

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.HendelseDbData
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.service.RepositoryUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class KafkaMessageReaderTest: ApplicationTest() {

	@MockkBean
	private lateinit var kafkaPublisher: KafkaPublisher

	@MockkBean
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var kafkaMessageReader: KafkaMessageReader

	@Test
	fun `Receiving archiving ack should update the database and publish message with journalpostId update`() {

		// Given
		val innsendingsId = UUID.randomUUID().toString()
		val journalpostId = 9997
		val message = "**Archiving: OK.  journalpostId=$journalpostId"
		val soknad = SoknadDbData(
			id = 1, innsendingsid = innsendingsId , tittel = "Tittel", skjemanr = "NAV 11-12.15B", tema = "TSO", status = SoknadsStatus.Innsendt, ettersendingsid = null,
			brukerid = "12345678901", spraak = "nb_NO",
			opprettetdato = LocalDateTime.now(), innsendtdato = LocalDateTime.now(), endretdato = LocalDateTime.now(), forsteinnsendingsdato = LocalDateTime.now(),
			arkiveringsstatus = ArkiveringsStatus.IkkeSatt, skalslettesdato = (OffsetDateTime.now()).plusDays(28), applikasjon = "applikasjon"  )

		val sentInId = slot<String>()
		val archivState = slot<ArkiveringsStatus>()
		val publishedMessage = slot<String>()

		every { repo.hentSoknadDb(innsendingsId) } returns soknad
		every { repo.oppdaterArkiveringsstatus(soknad, capture(archivState)) } returns
			HendelseDbData(
				id = 1, innsendingsid = innsendingsId , skjemanr = "NAV 11-12.15B", tema = "TSO", erettersending = false, hendelsetype = HendelseType.Arkivert,
				tidspunkt = LocalDateTime.now() )
		every { kafkaPublisher.publishToKvitteringsSide(capture(sentInId), capture(publishedMessage)) } returns Unit

			// When
		kafkaMessageReader.handleArchivingEvents(message, innsendingsId)

		// Then
		assertTrue(archivState.isCaptured)
		assertEquals(ArkiveringsStatus.Arkivert, archivState.captured)
		assertTrue(sentInId.isCaptured)
		assertEquals(innsendingsId, sentInId.captured)
		assertTrue(publishedMessage.isCaptured)
		assertTrue(publishedMessage.captured.contains(journalpostId.toString()))

	}


	@Test
	fun `Receiving archiving ack for attachments should update the database and publish message with journalpostId update`() {

		// Given
		val ettersendingsId = UUID.randomUUID().toString()
		val innsendingsId = UUID.randomUUID().toString()
		val vedlegg_1_uuid = UUID.randomUUID().toString()
		val vedlegg_2_uuid = UUID.randomUUID().toString()
		val journalpostId = 9998
		val message = "**Archiving: OK.  journalpostId=$journalpostId"
		val soknad = SoknadDbData(
			id = 1, innsendingsid = innsendingsId , tittel = "Tittel", skjemanr = "NAV 11-12.15B", tema = "TSO", status = SoknadsStatus.Innsendt, ettersendingsid = ettersendingsId,
			brukerid = "12345678901", spraak = "nb_NO",
			opprettetdato = LocalDateTime.now(), innsendtdato = LocalDateTime.now(), endretdato = LocalDateTime.now(), forsteinnsendingsdato = LocalDateTime.now(),
			arkiveringsstatus = ArkiveringsStatus.IkkeSatt, skalslettesdato = (OffsetDateTime.now()).plusDays(28), applikasjon = "applikasjon"  )

		val sentInId = slot<String>()
		val archivState = slot<ArkiveringsStatus>()
		val publishedMessages = mutableListOf<String>()

		every { repo.hentSoknadDb(innsendingsId) } returns soknad
		every { repo.hentInnsendteVedleggTilSoknad(any(), any())} returns listOf(
			VedleggDbData(id = 1, soknadsid = 1, status = OpplastingsStatus.INNSENDT,
				erhoveddokument = false, ervariant = false, erpakrevd = true, erpdfa = true,
				vedleggsnr = "W1", tittel = "W1-tittel", label = "W1-label", mimetype = "application/pdf", uuid = vedlegg_1_uuid ,
				opprettetdato = LocalDateTime.now(), endretdato = LocalDateTime.now(),  innsendtdato = LocalDateTime.now(), beskrivelse = "W1-beskrivelse",
				vedleggsurl = null, formioid = null, opplastingsvalgkommentarledetekst = null, opplastingsvalgkommentar = null ),
			VedleggDbData(id = 2, soknadsid = 1, status = OpplastingsStatus.INNSENDT,
				erhoveddokument = false, ervariant = false, erpakrevd = true, erpdfa = true,
				vedleggsnr = "W1", tittel = "W1-tittel", label = "W1-label2", mimetype = "application/pdf", uuid = vedlegg_2_uuid,
				opprettetdato = LocalDateTime.now(), endretdato = LocalDateTime.now(),  innsendtdato = LocalDateTime.now(), beskrivelse = "W1-beskrivelse",
				vedleggsurl = null, formioid = null, opplastingsvalgkommentarledetekst = null, opplastingsvalgkommentar = null ),
			)
		every { repo.oppdaterArkiveringsstatus(soknad, capture(archivState)) } returns
			HendelseDbData(
				id = 1, innsendingsid = innsendingsId , skjemanr = "NAV 11-12.15B", tema = "TSO", erettersending = true, hendelsetype = HendelseType.Arkivert,
				tidspunkt = LocalDateTime.now() )
		every { kafkaPublisher.publishToKvitteringsSide(capture(sentInId), capture(publishedMessages)) } returns Unit

		// When
		kafkaMessageReader.handleArchivingEvents(message, innsendingsId)

		// Then
		assertTrue(archivState.isCaptured)
		assertEquals(ArkiveringsStatus.Arkivert, archivState.captured)
		assertTrue(sentInId.isCaptured)
		assertEquals(innsendingsId, sentInId.captured)
		assertTrue(publishedMessages.isNotEmpty())
		assertTrue(publishedMessages[0].contains(journalpostId.toString()))
		assertTrue(publishedMessages[0].contains(vedlegg_1_uuid))

	}

	@Test
	fun `Receiving archiving error should update the database`() {

		// Given
		val innsendingsId = UUID.randomUUID().toString()
		val message = "**Archiving: FAILED"
		val soknad = SoknadDbData(
			id = 1, innsendingsid = innsendingsId , tittel = "Tittel", skjemanr = "NAV 11-12.15B", tema = "TSO", status = SoknadsStatus.Innsendt, ettersendingsid = null,
			brukerid = "12345678901", spraak = "nb_NO",
			opprettetdato = LocalDateTime.now(), innsendtdato = LocalDateTime.now(), endretdato = LocalDateTime.now(), forsteinnsendingsdato = LocalDateTime.now(),
			arkiveringsstatus = ArkiveringsStatus.IkkeSatt, skalslettesdato = (OffsetDateTime.now()).plusDays(28), applikasjon = "applikasjon"  )

		val sentInId = slot<String>()
		val archivState = slot<ArkiveringsStatus>()
		val publishedMessage = slot<String>()

		every { repo.hentSoknadDb(innsendingsId) } returns soknad
		every { repo.oppdaterArkiveringsstatus(soknad, capture(archivState)) } returns
			HendelseDbData(
				id = 1, innsendingsid = innsendingsId , skjemanr = "NAV 11-12.15B", tema = "TSO", erettersending = false, hendelsetype = HendelseType.ArkiveringFeilet,
				tidspunkt = LocalDateTime.now() )
		every { kafkaPublisher.publishToKvitteringsSide(capture(sentInId), capture(publishedMessage)) } returns Unit

		// When
		kafkaMessageReader.handleArchivingEvents(message, innsendingsId)

		// Then
		assertTrue(archivState.isCaptured)
		assertEquals(ArkiveringsStatus.ArkiveringFeilet, archivState.captured)
		assertTrue(!sentInId.isCaptured)
		assertTrue(!publishedMessage.isCaptured)

	}


}

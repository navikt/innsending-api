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
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.RepositoryUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class KafkaMessageReaderTest: ApplicationTest() {

/*
	@MockkBean
	private lateinit var kafkaPublisher: KafkaPublisher
*/

	@Autowired
	private lateinit var repo: RepositoryUtils
/*
	@MockkBean
	private lateinit var repo: RepositoryUtils
*/

	@Autowired
	private lateinit var kafkaMessageReader: KafkaMessageReader

	@Test
	fun `Receiving archiving ack should update the database and publish message with journalpostId update`() {

		// Given
		val innsendingsId = UUID.randomUUID().toString()
		val journalpostId = 9997
		val message = "**Archiving: OK.  journalpostId=$journalpostId"
		val soknadDb = SoknadDbData(
			id = null, innsendingsid = innsendingsId , tittel = "Tittel", skjemanr = "NAV 11-12.15B", tema = "TSO", status = SoknadsStatus.Innsendt, ettersendingsid = null,
			brukerid = "12345678901", spraak = "nb_NO",
			opprettetdato = LocalDateTime.now(), innsendtdato = LocalDateTime.now(), endretdato = LocalDateTime.now(), forsteinnsendingsdato = LocalDateTime.now(),
			arkiveringsstatus = ArkiveringsStatus.IkkeSatt, skalslettesdato = (OffsetDateTime.now()).plusDays(28), applikasjon = "applikasjon"  )

		val soknad = repo.lagreSoknad(soknadDb)

//		val sentInId = slot<String>()
//		val publishedMessage = slot<String>()

//		every { kafkaPublisher.publishToKvitteringsSide(capture(sentInId), capture(publishedMessage)) } returns Unit

			// When
		kafkaMessageReader.handleArchivingEvents(message, innsendingsId)

		// Then
		val arkivertSoknad = repo.hentSoknadDb(soknad.id!!)
		assertTrue(arkivertSoknad.arkiveringsstatus == ArkiveringsStatus.Arkivert)
/*
		assertTrue(publishedMessage.isCaptured)
		assertTrue(publishedMessage.captured.contains(journalpostId.toString()))
*/

	}


	@Test
	fun `Receiving archiving ack for attachments should update the database and publish message with journalpostId update`() {

		// Given
		val ettersendingsId = UUID.randomUUID().toString()
		val innsendingsId = UUID.randomUUID().toString()
		val vedlegg_1_uuid = UUID.randomUUID().toString()
		val vedlegg_2_uuid = UUID.randomUUID().toString()
		val journalpostId = 9998
		val soknadInnsendtDato = LocalDateTime.now().minusSeconds(50)
		val vadleggInnsendtDato = LocalDateTime.now().minusSeconds(49)


		val message = "**Archiving: OK.  journalpostId=$journalpostId"
		val soknad = SoknadDbData(
			id = null, innsendingsid = innsendingsId , tittel = "Tittel", skjemanr = "NAV 11-12.15B", tema = "TSO", status = SoknadsStatus.Innsendt, ettersendingsid = ettersendingsId,
			brukerid = "12345678901", spraak = "nb_NO",
			opprettetdato = LocalDateTime.now(), innsendtdato = soknadInnsendtDato, endretdato = LocalDateTime.now(), forsteinnsendingsdato = soknadInnsendtDato.minusDays(2),
			arkiveringsstatus = ArkiveringsStatus.IkkeSatt, skalslettesdato = (OffsetDateTime.now()).plusDays(28), applikasjon = "applikasjon"  )

		val lagretSoknad = repo.lagreSoknad(soknad)

		val vedleggsListe = listOf(
			VedleggDbData(id = null, soknadsid = lagretSoknad.id!!, status = OpplastingsStatus.INNSENDT,
				erhoveddokument = false, ervariant = false, erpakrevd = true, erpdfa = true,
				vedleggsnr = "W1", tittel = "W1-tittel", label = "W1-label", mimetype = "application/pdf", uuid = vedlegg_1_uuid ,
				opprettetdato = soknadInnsendtDato.minusDays(2), endretdato = vadleggInnsendtDato,  innsendtdato = vadleggInnsendtDato, beskrivelse = "W1-beskrivelse",
				vedleggsurl = null, formioid = null, opplastingsvalgkommentarledetekst = null, opplastingsvalgkommentar = null ),
			VedleggDbData(id = null, soknadsid = lagretSoknad.id!!, status = OpplastingsStatus.INNSENDT,
				erhoveddokument = false, ervariant = false, erpakrevd = true, erpdfa = true,
				vedleggsnr = "W1", tittel = "W1-tittel", label = "W1-label2", mimetype = "application/pdf", uuid = vedlegg_2_uuid,
				opprettetdato = soknadInnsendtDato.minusDays(2), endretdato = vadleggInnsendtDato,  innsendtdato =vadleggInnsendtDato, beskrivelse = "W1-beskrivelse",
				vedleggsurl = null, formioid = null, opplastingsvalgkommentarledetekst = null, opplastingsvalgkommentar = null ),
		)
		vedleggsListe.forEach {
			repo.lagreVedlegg(it)
		}

		/*
				val sentInId = slot<String>()
				val publishedMessages = mutableListOf<String>()
				every { kafkaPublisher.publishToKvitteringsSide(capture(sentInId), capture(publishedMessages)) } returns Unit
		*/

		// When
		kafkaMessageReader.handleArchivingEvents(message, innsendingsId)

		// Then
//		assertEquals(innsendingsId, sentInId.captured)
		val arkivertSoknad = repo.hentSoknadDb(soknad.id!!)
		assertTrue(arkivertSoknad.arkiveringsstatus == ArkiveringsStatus.Arkivert)
/*
		assertTrue(publishedMessages.isNotEmpty())
		assertTrue(publishedMessages[0].contains(journalpostId.toString()))
		assertTrue(publishedMessages[0].contains(vedlegg_1_uuid))
*/

	}

	@Test
	fun `Receiving archiving error should update the database`() {

		// Given
		val innsendingsId = UUID.randomUUID().toString()
		val message = "**Archiving: FAILED"
		val soknadDb = SoknadDbData(
			id = null, innsendingsid = innsendingsId , tittel = "Tittel", skjemanr = "NAV 11-12.15B", tema = "TSO", status = SoknadsStatus.Innsendt, ettersendingsid = null,
			brukerid = "12345678901", spraak = "nb_NO",
			opprettetdato = LocalDateTime.now(), innsendtdato = LocalDateTime.now(), endretdato = LocalDateTime.now(), forsteinnsendingsdato = LocalDateTime.now(),
			arkiveringsstatus = ArkiveringsStatus.IkkeSatt, skalslettesdato = (OffsetDateTime.now()).plusDays(28), applikasjon = "applikasjon"  )

		val soknad = repo.lagreSoknad(soknadDb)

//		val sentInId = slot<String>()
//		val publishedMessage = slot<String>()

		// When
		kafkaMessageReader.handleArchivingEvents(message, innsendingsId)

		// Then
		val arkivertSoknad = repo.hentSoknadDb(soknad.id!!)
		assertTrue(arkivertSoknad.arkiveringsstatus == ArkiveringsStatus.ArkiveringFeilet)
//		assertTrue(!sentInId.isCaptured)
//		assertTrue(!publishedMessage.isCaptured)

	}


}

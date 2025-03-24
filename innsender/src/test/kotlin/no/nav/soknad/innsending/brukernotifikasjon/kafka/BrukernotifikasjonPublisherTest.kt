package no.nav.soknad.innsending.brukernotifikasjon.kafka

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.slot
import io.mockk.verify
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.arkivering.soknadsmottaker.model.Varsel
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.brukernotifikasjon.NotificationOptions
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.util.soknaddbdata.getSkjemaPath
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class BrukernotifikasjonPublisherTest : ApplicationTest() {

	@SpykBean
	lateinit var sendTilPublisher: PublisherInterface

	@Autowired
	lateinit var restConfig: RestConfig

	@Autowired
	lateinit var brukernotifikasjonPublisher: BrukernotifikasjonPublisher

	private val defaultSkjemanr = "NAV 55-00.60"
	private val defaultTema = "BID"
	private var fyllutUrl: String? = null
	private var sendinnUrl: String? = null

	@BeforeEach
	fun setUp() {
		clearAllMocks()
		fyllutUrl = restConfig.fyllut.urls["default"]
		sendinnUrl = restConfig.sendinn.urls["default"]
	}

	@Test
	fun `sjekk at melding for å publisere Oppgave eller Beskjed blir sendt ved oppretting av ny søknad`() {
		val spraak = "no"
		val personId = "12125912345"
		val innsendingsid = "123456"

		val soknad = SoknadDbDataTestBuilder(
			brukerId = personId,
			spraak = spraak,
			innsendingsId = innsendingsid,
			status = SoknadsStatus.Opprettet,
		).build()
		brukernotifikasjonPublisher.createNotification(soknad)

		val message = slot<AddNotification>()
		verify(exactly = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) }

		assertTrue(message.isCaptured)
		assertEquals(personId, message.captured.soknadRef.personId)
		assertEquals(innsendingsid, message.captured.soknadRef.innsendingId)
		assertTrue(message.captured.brukernotifikasjonInfo.notifikasjonsTittel.contains(soknad.tittel))
		assertEquals(
			brukernotifikasjonPublisher.tittelPrefixNySoknad[spraak]!! + soknad.tittel,
			message.captured.brukernotifikasjonInfo.notifikasjonsTittel
		)
		assertEquals(
			"$fyllutUrl/${soknad.getSkjemaPath()}/oppsummering?sub=digital&innsendingsId=$innsendingsid",
			message.captured.brukernotifikasjonInfo.lenke
		)
		assertEquals(0, message.captured.brukernotifikasjonInfo.eksternVarsling.size)
	}

	@Test
	fun `sjekk at melding for å publisere Done blir sendt ved innsending av søknad`() {
		val innsendingsid = "123456"
		val personId = "12125912345"

		val soknad = SoknadDbDataTestBuilder(
			brukerId = personId,
			innsendingsId = innsendingsid,
			status = SoknadsStatus.Innsendt,
		).build()
		brukernotifikasjonPublisher.closeNotification(soknad)

		val done = slot<SoknadRef>()
		verify(exactly = 1) { sendTilPublisher.avsluttBrukernotifikasjon(capture(done)) }

		assertTrue(done.isCaptured)
		assertEquals(personId, done.captured.personId)
		assertEquals(innsendingsid, done.captured.innsendingId)
	}

	@Test
	fun `sjekk at brukernotifikasjon opprettes for ny ettersending`() {
		val innsendingsid = "123456"
		val ettersendingsSoknadsId = "123457"
		val spraak = "no"
		val personId = "12125912345"

		val ettersending = SoknadDbDataTestBuilder(
			brukerId = personId,
			spraak = spraak,
			innsendingsId = ettersendingsSoknadsId,
			status = SoknadsStatus.Opprettet,
			ettersendingsId = innsendingsid
		).build()
		brukernotifikasjonPublisher.createNotification(ettersending)

		val message = slot<AddNotification>()
		verify(exactly = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) }
		assertTrue(message.isCaptured)
		val addNotification = message.captured

		assertEquals(personId, addNotification.soknadRef.personId)
		assertEquals(ettersending.innsendingsid, addNotification.soknadRef.innsendingId)
		assertEquals(ettersending.ettersendingsid, addNotification.soknadRef.groupId)
		assertTrue(addNotification.brukernotifikasjonInfo.notifikasjonsTittel.contains(ettersending.tittel))
		assertEquals(
			brukernotifikasjonPublisher.tittelPrefixEttersendelse[spraak]!! + ettersending.tittel,
			addNotification.brukernotifikasjonInfo.notifikasjonsTittel
		)
		assertEquals(
			"$sendinnUrl/$ettersendingsSoknadsId",
			addNotification.brukernotifikasjonInfo.lenke
		)
		assertEquals(Varsel.Kanal.sms, addNotification.brukernotifikasjonInfo.eksternVarsling[0].kanal)
	}

	@Test
	fun `sjekk at melding blir sendt for publisering av Done etter innsending av ettersendingssøknad`() {
		val innsendingsid = "123456"
		val ettersendingsSoknadsId = "123457"
		val skjemanr = defaultSkjemanr
		val tema = defaultTema
		val spraak = "no"
		val personId = "12125912345"
		val tittel = "Dokumentasjon av utdanning"

		val ettersending = SoknadDbDataTestBuilder(
			brukerId = personId,
			skjemanr = skjemanr,
			spraak = spraak,
			tittel = tittel,
			tema = tema,
			innsendingsId = ettersendingsSoknadsId,
			status = SoknadsStatus.Innsendt,
			ettersendingsId = innsendingsid
		).build()
		brukernotifikasjonPublisher.closeNotification(ettersending)

		val avslutninger = mutableListOf<SoknadRef>()
		verify(exactly = 1) { sendTilPublisher.avsluttBrukernotifikasjon(capture(avslutninger)) }

		assertTrue(avslutninger.isNotEmpty())
		assertEquals(personId, avslutninger[0].personId)
		assertEquals(ettersendingsSoknadsId, avslutninger[0].innsendingId)
		assertEquals(innsendingsid, avslutninger[0].groupId)
		assertTrue(avslutninger[0].erEttersendelse)
		assertNotNull(avslutninger[0].tidpunktEndret)
	}

	@Test
	fun `Should create notification with fyllut url when visningsType=fyllut`() {
		// Given
		val fyllutSoknad = SoknadDbDataTestBuilder(visningsType = VisningsType.fyllUt).build()

		// When
		brukernotifikasjonPublisher.createNotification(fyllutSoknad)

		// Then
		val message = slot<AddNotification>()
		verify(exactly = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) }

		assertTrue(message.isCaptured)
		assertEquals(
			"$fyllutUrl/${fyllutSoknad.getSkjemaPath()}/oppsummering?sub=digital&innsendingsId=${fyllutSoknad.innsendingsid}",
			message.captured.brukernotifikasjonInfo.lenke
		)
	}

	@Test
	fun `Should create notification with sendinn url when visningsType=dokumentinnsending`() {
		// Given
		val dokumentinnsendingSoknad = SoknadDbDataTestBuilder(visningsType = VisningsType.dokumentinnsending).build()

		// When
		brukernotifikasjonPublisher.createNotification(dokumentinnsendingSoknad)

		// Then
		val message = slot<AddNotification>()
		verify(exactly = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) }

		assertTrue(message.isCaptured)
		assertEquals(
			"$sendinnUrl/${dokumentinnsendingSoknad.innsendingsid}",
			message.captured.brukernotifikasjonInfo.lenke
		)
		assertNull(message.captured.brukernotifikasjonInfo.utsettSendingTil)
	}

	@Test
	fun `Should create notification with sendinn url when visningsType=ettersending`() {
		// Given
		val ettersending = SoknadDbDataTestBuilder(visningsType = VisningsType.ettersending).build()

		// When
		brukernotifikasjonPublisher.createNotification(ettersending)

		// Then
		val message = slot<AddNotification>()
		verify(exactly = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) }

		assertTrue(message.isCaptured)
		assertEquals(
			"$sendinnUrl/${ettersending.innsendingsid}",
			message.captured.brukernotifikasjonInfo.lenke
		)
	}

	@Test
	fun `Should create notification with utsattSending when systemGenerert=true`() {
		// Given
		val ettersending = SoknadDbDataTestBuilder().build()

		// When
		brukernotifikasjonPublisher.createNotification(ettersending, NotificationOptions(erSystemGenerert = true))

		// Then
		val message = slot<AddNotification>()
		verify(exactly = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) }

		assertTrue(message.isCaptured)
		assertNotNull(message.captured.brukernotifikasjonInfo.utsettSendingTil)
		assertEquals(true, message.captured.soknadRef.erSystemGenerert)
	}

	@Test
	fun `Should create notification without utsattSending when systemGenerert=false`() {
		// Given
		val ettersending = SoknadDbDataTestBuilder().build()

		// When
		brukernotifikasjonPublisher.createNotification(ettersending, NotificationOptions(erSystemGenerert = false))

		// Then
		val message = slot<AddNotification>()
		verify(exactly = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) }

		assertTrue(message.isCaptured)
		assertNull(message.captured.brukernotifikasjonInfo.utsettSendingTil)
		assertEquals(false, message.captured.soknadRef.erSystemGenerert)
	}

	@Test
	fun `Should keep notification active through the lifespan of the soknad`() {
		// Given
		val mellomlagringDager = 10
		val soknad = SoknadDbDataTestBuilder(
			skalslettesdato = OffsetDateTime.now().plusDays(mellomlagringDager.toLong())
		).build()

		// When
		brukernotifikasjonPublisher.createNotification(soknad)

		// Then
		val message = slot<AddNotification>()
		verify(exactly = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) }

		assertTrue(message.isCaptured)
		assertEquals(mellomlagringDager, message.captured.brukernotifikasjonInfo.antallAktiveDager)
		assertNull(message.captured.brukernotifikasjonInfo.utsettSendingTil)
	}


}

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
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.util.soknaddbdata.getSkjemaPath
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.lang.Thread.sleep
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class BrukernotifikasjonPublisherTest : ApplicationTest() {

	@Autowired
	lateinit var soknadRepository: SoknadRepository

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
	// Vi fjerner søknader/ettersendinger etter midnatt dagen etter slettetdato er passert. Da skal også brukernotifikasjoner slettes.
	// I og med at vi har implementert sletting natten til dagen etter slettedatoen, og slettedatoen vises på brukernotifikasjonen,
	// må vi passe på å legge til en ekstra dag i meldingen til soknadsmottaker.
	fun testConverteringFraDatoTilDager() {
		// Given
		val soknad = SoknadDbDataTestBuilder(
			skalslettesdato = OffsetDateTime.now().plusDays(10)
		).build()
		soknadRepository.save(soknad)

		// When
		brukernotifikasjonPublisher.createNotification(soknad)

		// then
		val message = slot<AddNotification>()
		verify(exactly = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) }

		assertTrue(message.isCaptured)
		val numberOfDays = message.captured.brukernotifikasjonInfo.antallAktiveDager
		val deleteDateTime = zonedDateTimeFromDays(numberOfDays)
		assertEquals(11, message.captured.brukernotifikasjonInfo.antallAktiveDager)
		assertEquals(11, deleteDateTime.toLocalDate().toEpochDay() - OffsetDateTime.now(ZoneId.of("Europe/Oslo")).toLocalDate().toEpochDay())
	}

	private fun zonedDateTimeFromDays(days: Int): ZonedDateTime {
		val now = OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
		return now
			.toLocalDate()
			.plusDays(days.toLong())
			.atTime(0, 5)
			.atZone(now.offset)
	}


	@Test
	fun `sjekk at melding for å publisere Oppgave eller Beskjed blir sendt ved oppretting av ny søknad`() {
		// Given
		val spraak = "no"
		val personId = "12125912345"
		val innsendingsid = UUID.randomUUID().toString()

		val soknad = SoknadDbDataTestBuilder(
			brukerId = personId,
			spraak = spraak,
			innsendingsId = innsendingsid,
			status = SoknadsStatus.Opprettet,
		).build()
		soknadRepository.save(soknad)

		// When
		brukernotifikasjonPublisher.createNotification(soknad)

		// Then
		val messages = mutableListOf<AddNotification>()
		verify(atLeast = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(messages)) }
		assertTrue(messages.isNotEmpty())
		val messages_ = messages.filter { it.soknadRef.innsendingId == innsendingsid }
		assertTrue(messages_.isNotEmpty() && messages_.size == 1)
		val message = messages_.first()
		assertEquals(personId, message.soknadRef.personId)
		assertTrue(message.brukernotifikasjonInfo.notifikasjonsTittel.contains(soknad.tittel))
		assertEquals(
			brukernotifikasjonPublisher.tittelPrefixNySoknad[spraak]!! + soknad.tittel,
			message.brukernotifikasjonInfo.notifikasjonsTittel
		)
		assertEquals(
			"$fyllutUrl/${soknad.getSkjemaPath()}/oppsummering?sub=digital&innsendingsId=$innsendingsid",
			message.brukernotifikasjonInfo.lenke
		)
		assertEquals(0, message.brukernotifikasjonInfo.eksternVarsling.size)
	}

	@Test
	fun `sjekk at melding for å publisere Done blir sendt ved innsending av søknad`() {
		// Given
		val innsendingsid = UUID.randomUUID().toString()
		val personId = "12125912345"

		val soknad = SoknadDbDataTestBuilder(
			brukerId = personId,
			innsendingsId = innsendingsid,
			status = SoknadsStatus.Innsendt,
		).build()
		soknadRepository.save(soknad)

		// When
		brukernotifikasjonPublisher.closeNotification(soknad)

		// Then
		val done = mutableListOf<SoknadRef>()
		verify(timeout = 500, atLeast = 1) { sendTilPublisher.avsluttBrukernotifikasjon(capture(done)) }

		assertTrue(done.isNotEmpty())
		assertTrue(done.any { it.innsendingId == innsendingsid })
		assertEquals(personId, done.filter{it.innsendingId == innsendingsid}.first().personId)
		assertTrue(done.filter{it.innsendingId == innsendingsid}.size== 1)
	}

	@Test
	fun `sjekk at brukernotifikasjon opprettes for ny ettersending`() {
		// Given
		val innsendingsid = UUID.randomUUID().toString()
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
		soknadRepository.save(ettersending)

		// When
		brukernotifikasjonPublisher.createNotification(ettersending)

		// Then
		val message = slot<AddNotification>()
		verify(timeout = 500, exactly = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) }
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
		// Given
		val innsendingsid = UUID.randomUUID().toString()
		val ettersendingsSoknadsId = UUID.randomUUID().toString()
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
		soknadRepository.save(ettersending)

		// When
		brukernotifikasjonPublisher.closeNotification(ettersending)

		// Then
		val avslutninger = mutableListOf<SoknadRef>()
		verify(timeout = 500, atLeast = 1) { sendTilPublisher.avsluttBrukernotifikasjon(capture(avslutninger)) }


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
		soknadRepository.save(fyllutSoknad)

		// When
		brukernotifikasjonPublisher.createNotification(fyllutSoknad)

		// Then
		val messages = mutableListOf<AddNotification>()
		verify(timeout = 500, atLeast = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(messages)) }

		assertTrue(messages.isNotEmpty())
		assertTrue(messages.filter { it.soknadRef.innsendingId == fyllutSoknad.innsendingsid }.size == 1)
		val message = messages.filter { it.soknadRef.innsendingId == fyllutSoknad.innsendingsid }.first()
		assertEquals(
			"$fyllutUrl/${fyllutSoknad.getSkjemaPath()}/oppsummering?sub=digital&innsendingsId=${fyllutSoknad.innsendingsid}",
			message.brukernotifikasjonInfo.lenke
		)
	}

	@Test
	fun `Should create notification with sendinn url when visningsType=dokumentinnsending`() {
		// Given
		val dokumentinnsendingSoknad = SoknadDbDataTestBuilder(visningsType = VisningsType.dokumentinnsending).build()
		soknadRepository.save(dokumentinnsendingSoknad)

		// When
		brukernotifikasjonPublisher.createNotification(dokumentinnsendingSoknad)

		// Then
		val messages = mutableListOf<AddNotification>()
		verify(atLeast = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(messages)) }

		assertTrue(messages.isNotEmpty())
		assertTrue(messages.filter { it.soknadRef.innsendingId == dokumentinnsendingSoknad.innsendingsid }.size == 1)
		val message = messages.filter { it.soknadRef.innsendingId == dokumentinnsendingSoknad.innsendingsid }.first()
		assertEquals(
			"$sendinnUrl/${dokumentinnsendingSoknad.innsendingsid}",
			message.brukernotifikasjonInfo.lenke
		)
		assertNull(message.brukernotifikasjonInfo.utsettSendingTil)
	}

	@Test
	fun `Should create notification with sendinn url when visningsType=ettersending`() {
		// Given
		val ettersending = SoknadDbDataTestBuilder(visningsType = VisningsType.ettersending).build()
		soknadRepository.save(ettersending)

		// When
		brukernotifikasjonPublisher.createNotification(ettersending)

		// Then
		val messages = mutableListOf<AddNotification>()
		verify(atLeast = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(messages)) }

		assertTrue(messages.isNotEmpty())
		assertTrue(messages.filter { it.soknadRef.innsendingId == ettersending.innsendingsid }.size == 1)
		val message = messages.filter { it.soknadRef.innsendingId == ettersending.innsendingsid }.first()
		assertEquals(
			"$sendinnUrl/${ettersending.innsendingsid}",
			message.brukernotifikasjonInfo.lenke
		)
	}

	@Test
	fun `Should create notification with utsattSending when systemGenerert=true`() {
		// Given
		val ettersending = SoknadDbDataTestBuilder().build()
		soknadRepository.save(ettersending)

		// When
		brukernotifikasjonPublisher.createNotification(ettersending, NotificationOptions(erSystemGenerert = true))

		// Then
		val messages = mutableListOf<AddNotification>()
		verify(atLeast = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(messages)) }

		assertTrue(messages.isNotEmpty())
		assertTrue(messages.filter { it.soknadRef.innsendingId == ettersending.innsendingsid }.size == 1)
		val message = messages.filter { it.soknadRef.innsendingId == ettersending.innsendingsid }.first()

		assertEquals(true, message.soknadRef.erSystemGenerert)
	}

	@Test
	fun `Should create notification without utsattSending when systemGenerert=false`() {
		// Given
		val ettersending = SoknadDbDataTestBuilder().build()
		soknadRepository.save(ettersending)

		// When
		brukernotifikasjonPublisher.createNotification(ettersending, NotificationOptions(erSystemGenerert = false))

		// Then
		val messages = mutableListOf<AddNotification>()
		verify(atLeast = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(messages)) }

		assertTrue(messages.isNotEmpty())
		assertTrue(messages.filter { it.soknadRef.innsendingId == ettersending.innsendingsid }.size == 1)
		val message = messages.filter { it.soknadRef.innsendingId == ettersending.innsendingsid }.first()
		assertNull(message.brukernotifikasjonInfo.utsettSendingTil)
		assertEquals(false, message.soknadRef.erSystemGenerert)
	}

	@Test
	fun `Should keep notification active through the lifespan of the soknad`() {
		// Given
		val mellomlagringDager = 10
		val soknad = SoknadDbDataTestBuilder(
			skalslettesdato = OffsetDateTime.now().plusDays(mellomlagringDager.toLong())
		).build()
		soknadRepository.save(soknad)

		// When
		brukernotifikasjonPublisher.createNotification(soknad)

		// Then
		val messages = mutableListOf<AddNotification>()
		verify(atLeast = 1) { sendTilPublisher.opprettBrukernotifikasjon(capture(messages)) }

		assertTrue(messages.isNotEmpty())
		assertTrue(messages.filter { it.soknadRef.innsendingId == soknad.innsendingsid }.size == 1)
		val message = messages.filter { it.soknadRef.innsendingId == soknad.innsendingsid }.first()
		assertEquals(mellomlagringDager + 1, message.brukernotifikasjonInfo.antallAktiveDager)
		assertNull(message.brukernotifikasjonInfo.utsettSendingTil)
	}


	@Test
	fun `Should not send new notification if soknad not editable`() {
		// Given
		val mellomlagringDager = 10
		val soknad = SoknadDbDataTestBuilder(
			skalslettesdato = OffsetDateTime.now().plusDays(mellomlagringDager.toLong()),
			status = SoknadsStatus.SlettetAvBruker
		).build()
		soknadRepository.save(soknad)

		// When
		val response = brukernotifikasjonPublisher.createNotification(soknad)

		// Then
		assertEquals(false, response)
		val message = slot<AddNotification>()
		verify(exactly = 0) { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) }

	}

}

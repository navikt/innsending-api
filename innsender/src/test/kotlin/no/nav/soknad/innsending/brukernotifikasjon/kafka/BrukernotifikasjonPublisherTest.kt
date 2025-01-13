package no.nav.soknad.innsending.brukernotifikasjon.kafka

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.arkivering.soknadsmottaker.model.Varsel
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.BrukerNotifikasjonConfig
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class BrukernotifikasjonPublisherTest : ApplicationTest() {

	@Autowired
	private val notifikasjonConfig: BrukerNotifikasjonConfig = BrukerNotifikasjonConfig()

	@InjectMockKs
	var sendTilPublisher = mockk<PublisherInterface>()

	private var brukernotifikasjonPublisher: BrukernotifikasjonPublisher? = null
	private val defaultSkjemanr = "NAV 55-00.60"
	private val defaultTema = "BID"
	private val defaultTittel = "Avtale om barnebidrag"

	@BeforeEach
	fun setUp() {
		brukernotifikasjonPublisher = spyk(BrukernotifikasjonPublisher(notifikasjonConfig, sendTilPublisher))
	}

	@Test
	fun `sjekk at melding for å publisere Oppgave eller Beskjed blir sendt ved oppretting av ny søknad`() {
		val innsendingsid = "123456"
		val skjemanr = defaultSkjemanr
		val tema = defaultTema
		val spraak = "no"
		val personId = "12125912345"
		val tittel = "Dokumentasjon av utdanning"
		val id = 1L

		val message = slot<AddNotification>()
		every { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) } returns Unit

		brukernotifikasjonPublisher?.soknadStatusChange(
			Hjelpemetoder.lagDokumentSoknad(
				brukerId = personId,
				skjemanr = skjemanr,
				spraak = spraak,
				tittel = tittel,
				tema = tema,
				id = id,
				innsendingsid = innsendingsid,
				soknadstype = SoknadType.soknad,
			)
		)

		assertTrue(message.isCaptured)
		assertEquals(personId, message.captured.soknadRef.personId)
		assertEquals(innsendingsid, message.captured.soknadRef.innsendingId)
		assertTrue(message.captured.brukernotifikasjonInfo.notifikasjonsTittel.contains(tittel))
		assertEquals(
			brukernotifikasjonPublisher?.tittelPrefixNySoknad?.get(spraak)!! + tittel,
			message.captured.brukernotifikasjonInfo.notifikasjonsTittel
		)
		assertEquals(
			"${notifikasjonConfig.fyllutUrl}/nav550060/oppsummering?sub=digital&innsendingsId=$innsendingsid",
			message.captured.brukernotifikasjonInfo.lenke
		)
		assertEquals(0, message.captured.brukernotifikasjonInfo.eksternVarsling.size)
	}

	@Test
	fun `sjekk at melding for å publisere Done blir sendt ved innsending av søknad`() {
		val innsendingsid = "123456"
		val skjemanr = defaultSkjemanr
		val tema = defaultTema
		val spraak = "no"
		val personId = "12125912345"
		val tittel = "Dokumentasjon av utdanning"
		val id = 2L

		val done = slot<SoknadRef>()

		every { sendTilPublisher.avsluttBrukernotifikasjon(capture(done)) } returns Unit

		brukernotifikasjonPublisher?.soknadStatusChange(
			Hjelpemetoder.lagDokumentSoknad(
				personId,
				skjemanr,
				spraak,
				tittel,
				tema,
				id,
				innsendingsid,
				SoknadsStatusDto.Innsendt
			)
		)

		assertTrue(done.isCaptured)
		assertEquals(personId, done.captured.personId)
		assertEquals(innsendingsid, done.captured.innsendingId)
	}

	@Test
	fun `sjekk at melding om publisering av Done og Oppgave blir sendt ved innsending av søknad med vedlegg som skal ettersendes`() {
		val innsendingsid = "123456"
		val ettersendingsSoknadsId = "123457"
		val skjemanr = defaultSkjemanr
		val tema = defaultTema
		val spraak = "no"
		val personId = "12125912345"
		val tittel = "Dokumentasjon av utdanning"
		val id = 3L

		val done = slot<SoknadRef>()
		val oppgave = slot<AddNotification>()

		every { sendTilPublisher.avsluttBrukernotifikasjon(capture(done)) } returns Unit
		every { sendTilPublisher.opprettBrukernotifikasjon(capture(oppgave)) } returns Unit

		val soknad = Hjelpemetoder.lagDokumentSoknad(
			brukerId = personId,
			skjemanr = skjemanr,
			spraak = spraak,
			tittel = tittel,
			tema = tema,
			id = id,
			innsendingsid = innsendingsid,
			soknadsStatus = SoknadsStatusDto.Innsendt,
			vedleggsListe = listOf(
				Hjelpemetoder.lagVedlegg(1L, "X1", "Vedlegg-X1", OpplastingsStatusDto.Innsendt, false, "/litenPdf.pdf"),
				Hjelpemetoder.lagVedlegg(2L, "X2", "Vedlegg-X2", OpplastingsStatusDto.SendSenere, false)
			),
			soknadstype = SoknadType.ettersendelse
		)

		brukernotifikasjonPublisher?.soknadStatusChange(soknad)

		assertTrue(done.isCaptured)
		assertEquals(personId, done.captured.personId)
		assertEquals(innsendingsid, done.captured.innsendingId)

		val ettersending = Hjelpemetoder.lagDokumentSoknad(
			brukerId = personId,
			skjemanr = skjemanr,
			spraak = spraak,
			tittel = tittel,
			tema = tema,
			id = id,
			innsendingsid = ettersendingsSoknadsId,
			soknadsStatus = SoknadsStatusDto.Opprettet,
			vedleggsListe = listOf(
				Hjelpemetoder.lagVedlegg(1L, "X1", "Vedlegg-X1", OpplastingsStatusDto.Innsendt, false, "/litenPdf.pdf"),
				Hjelpemetoder.lagVedlegg(2L, "X2", "Vedlegg-X2", OpplastingsStatusDto.IkkeValgt, false)
			),
			ettersendingsId = soknad.innsendingsId,
			soknadstype = SoknadType.ettersendelse
		)

		brukernotifikasjonPublisher?.soknadStatusChange(ettersending)

		assertTrue(oppgave.isCaptured)
		assertEquals(personId, oppgave.captured.soknadRef.personId)
		assertEquals(ettersending.innsendingsId, oppgave.captured.soknadRef.innsendingId)
		assertEquals(ettersending.ettersendingsId, oppgave.captured.soknadRef.groupId)
		assertTrue(oppgave.captured.brukernotifikasjonInfo.notifikasjonsTittel.contains(tittel))
		assertEquals(
			brukernotifikasjonPublisher?.tittelPrefixEttersendelse?.get(spraak)!! + tittel,
			oppgave.captured.brukernotifikasjonInfo.notifikasjonsTittel
		)
		assertEquals(
			notifikasjonConfig.sendinnUrl + "/" + ettersendingsSoknadsId,
			oppgave.captured.brukernotifikasjonInfo.lenke
		)
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
		val id = 3L

		val avslutninger = mutableListOf<SoknadRef>()

		every { sendTilPublisher.avsluttBrukernotifikasjon(capture(avslutninger)) } returns Unit   // Nokkel(appConfiguration.kafkaConfig.username, innsendingsid )

		brukernotifikasjonPublisher?.soknadStatusChange(
			Hjelpemetoder.lagDokumentSoknad(
				personId, skjemanr, spraak, tittel, tema, id, ettersendingsSoknadsId, SoknadsStatusDto.Innsendt,
				listOf(
					Hjelpemetoder.lagVedlegg(1L, "X1", "Vedlegg-X1", OpplastingsStatusDto.LastetOpp, false, "/litenPdf.pdf"),
					Hjelpemetoder.lagVedlegg(2L, "X2", "Vedlegg-X2", OpplastingsStatusDto.LastetOpp, false, "/litenPdf.pdf")
				),
				innsendingsid
			)
		)

		assertTrue(avslutninger.isNotEmpty())
		assertEquals(personId, avslutninger[0].personId)
		assertEquals(ettersendingsSoknadsId, avslutninger[0].innsendingId)

	}

	@Test
	fun `sjekk at melding blir sendt for publisering av Done etter sletting av søknad`() {
		val innsendingsid = "123456"
		val skjemanr = defaultSkjemanr
		val tema = defaultTema
		val tittel = defaultTittel
		val spraak = "no"
		val personId = "12125912345"
		val id = 2L

		val done = slot<SoknadRef>()

		every { sendTilPublisher.avsluttBrukernotifikasjon(capture(done)) } returns Unit

		brukernotifikasjonPublisher?.soknadStatusChange(
			Hjelpemetoder.lagDokumentSoknad(
				personId,
				skjemanr,
				spraak,
				tittel,
				tema,
				id,
				innsendingsid,
				SoknadsStatusDto.SlettetAvBruker
			)
		)

		assertTrue(done.isCaptured)
		assertEquals(personId, done.captured.personId)
		assertEquals(innsendingsid, done.captured.innsendingId)

	}

	@Test
	fun `Skal sende sms varsling ved ettersending`() {
		// Gitt
		val innsendingsid = "123456"
		val skjemanr = defaultSkjemanr
		val tema = defaultTema
		val spraak = "no"
		val personId = "12125912345"
		val tittel = "Dokumentasjon av utdanning"
		val id = 1L
		val ettersendingsId = "2345678"

		val message = slot<AddNotification>()
		every { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) } returns Unit

		// Når
		brukernotifikasjonPublisher?.soknadStatusChange(
			Hjelpemetoder.lagDokumentSoknad(
				personId,
				skjemanr,
				spraak,
				tittel,
				tema,
				id,
				innsendingsid,
				ettersendingsId = ettersendingsId
			)
		)

		// Så
		assertTrue(message.isCaptured)
		assertEquals(Varsel.Kanal.sms, message.captured.brukernotifikasjonInfo.eksternVarsling[0].kanal)
	}

	@Test
	fun `Should create notification with fyllut url when visningsType=fyllut`() {
		// Given
		val dokumentSoknadDto = DokumentSoknadDtoTestBuilder(visningsType = VisningsType.fyllUt).build()

		val message = slot<AddNotification>()
		every { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) } returns Unit

		// When
		brukernotifikasjonPublisher?.soknadStatusChange(dokumentSoknadDto)

		// Then
		assertTrue(message.isCaptured)
		assertEquals(
			"http://localhost:3001/fyllut/${dokumentSoknadDto.skjemaPath}/oppsummering?sub=digital&innsendingsId=${dokumentSoknadDto.innsendingsId}",
			message.captured.brukernotifikasjonInfo.lenke
		)
	}

	@Test
	fun `Should create notification with sendinn url when visningsType=dokumentinnsending`() {
		// Given
		val dokumentSoknadDto = DokumentSoknadDtoTestBuilder(visningsType = VisningsType.dokumentinnsending).build()

		val message = slot<AddNotification>()
		every { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) } returns Unit

		// When
		brukernotifikasjonPublisher?.soknadStatusChange(dokumentSoknadDto)

		// Then
		assertTrue(message.isCaptured)
		assertEquals(
			"http://localhost:3100/sendinn/${dokumentSoknadDto.innsendingsId}",
			message.captured.brukernotifikasjonInfo.lenke
		)
		assertNull(message.captured.brukernotifikasjonInfo.utsettSendingTil)
	}

	@Test
	fun `Should create notification with sendinn url when visningsType=ettersending`() {
		// Given
		val dokumentSoknadDto = DokumentSoknadDtoTestBuilder(visningsType = VisningsType.ettersending).build()

		val message = slot<AddNotification>()
		every { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) } returns Unit

		// When
		brukernotifikasjonPublisher?.soknadStatusChange(dokumentSoknadDto)

		// Then
		assertTrue(message.isCaptured)
		assertEquals(
			"http://localhost:3100/sendinn/${dokumentSoknadDto.innsendingsId}",
			message.captured.brukernotifikasjonInfo.lenke
		)
	}

	@Test
	fun `Should create notification with utsattSending when systemGenerert=true`() {
		// Given
		val dokumentSoknadDto = DokumentSoknadDtoTestBuilder(
			visningsType = VisningsType.ettersending,
			erSystemGenerert = true
		).build()

		val message = slot<AddNotification>()
		every { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) } returns Unit

		// When
		brukernotifikasjonPublisher?.soknadStatusChange(dokumentSoknadDto)

		// Then
		assertTrue(message.isCaptured)
		assertNotNull(message.captured.brukernotifikasjonInfo.utsettSendingTil)
	}

	@Test
	fun `Should keep notification active through the lifespan of the soknad`() {
		// Given
		val mellomlagringDager = 10
		val dokumentSoknadDto =
			DokumentSoknadDtoTestBuilder(skalslettesdato = null, mellomlagringDager = mellomlagringDager).build()

		val message = slot<AddNotification>()
		every { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) } returns Unit

		// When
		brukernotifikasjonPublisher?.soknadStatusChange(dokumentSoknadDto)

		// Then
		assertTrue(message.isCaptured)
		assertEquals(mellomlagringDager, message.captured.brukernotifikasjonInfo.antallAktiveDager)
		assertNull(message.captured.brukernotifikasjonInfo.utsettSendingTil)
	}


}

package no.nav.soknad.innsending.brukernotifikasjon.kafka

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.arkivering.soknadsmottaker.model.Varsel
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.BrukerNotifikasjonConfig
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootTest
@ActiveProfiles("spring")
@EnableTransactionManagement
internal class BrukernotifikasjonPublisherTest {

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
				personId,
				skjemanr,
				spraak,
				tittel,
				tema,
				id,
				innsendingsid
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
		assertEquals(notifikasjonConfig.tjenesteUrl + "/" + innsendingsid, message.captured.brukernotifikasjonInfo.lenke)
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
				SoknadsStatusDto.innsendt
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
			personId, skjemanr, spraak, tittel, tema, id, innsendingsid, SoknadsStatusDto.innsendt,
			listOf(
				Hjelpemetoder.lagVedlegg(1L, "X1", "Vedlegg-X1", OpplastingsStatusDto.innsendt, false, "/litenPdf.pdf"),
				Hjelpemetoder.lagVedlegg(2L, "X2", "Vedlegg-X2", OpplastingsStatusDto.sendSenere, false)
			),
		)

		brukernotifikasjonPublisher?.soknadStatusChange(soknad)

		assertTrue(done.isCaptured)
		assertEquals(personId, done.captured.personId)
		assertEquals(innsendingsid, done.captured.innsendingId)

		val ettersending = Hjelpemetoder.lagDokumentSoknad(
			personId, skjemanr, spraak, tittel, tema, id, ettersendingsSoknadsId, SoknadsStatusDto.opprettet,
			listOf(
				Hjelpemetoder.lagVedlegg(1L, "X1", "Vedlegg-X1", OpplastingsStatusDto.innsendt, false, "/litenPdf.pdf"),
				Hjelpemetoder.lagVedlegg(2L, "X2", "Vedlegg-X2", OpplastingsStatusDto.ikkeValgt, false)
			), soknad.innsendingsId
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
			notifikasjonConfig.tjenesteUrl + "/" + ettersendingsSoknadsId,
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
				personId, skjemanr, spraak, tittel, tema, id, ettersendingsSoknadsId, SoknadsStatusDto.innsendt,
				listOf(
					Hjelpemetoder.lagVedlegg(1L, "X1", "Vedlegg-X1", OpplastingsStatusDto.lastetOpp, false, "/litenPdf.pdf"),
					Hjelpemetoder.lagVedlegg(2L, "X2", "Vedlegg-X2", OpplastingsStatusDto.lastetOpp, false, "/litenPdf.pdf")
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
				SoknadsStatusDto.slettetAvBruker
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


}

package no.nav.soknad.innsending.brukernotifikasjon.kafka

import no.nav.brukernotifikasjon.schemas.Beskjed
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.soknad.innsending.ProfileConfig
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.KafkaConfig
import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.SoknadsStatus
import no.nav.soknad.innsending.utils.lagDokumentSoknad
import no.nav.soknad.innsending.utils.lagVedlegg
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootTest
@ActiveProfiles("test")
@EnableTransactionManagement
internal class BrukernotifikasjonPublisherTest {

	@Autowired
	private val kafkaConfig: KafkaConfig = KafkaConfig(ProfileConfig("test"))

	@InjectMockKs
	var kafkaPublisher = mockk<KafkaPublisher>()

	private var brukernotifikasjonPublisher: BrukernotifikasjonPublisher? = null

	@BeforeEach
	fun setUp() {
		brukernotifikasjonPublisher = spyk(BrukernotifikasjonPublisher(kafkaConfig, kafkaPublisher))
	}

	@Test
	fun `sjekk at Beskjed melding blir publisert ved oppretting av ny dokumentinnsending`() {
		val innsendingsid = "123456"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val personId = "12125912345"
		val tema = "PEN"
		val tittel = "Dokumentasjon av utdanning"
		val id = 1L

		val message = slot<Beskjed>()
		every { kafkaPublisher.putApplicationMessageOnTopic(any(), capture(message)) } returns Unit

		brukernotifikasjonPublisher?.soknadStatusChange(lagDokumentSoknad(personId, skjemanr, spraak, tittel, tema, id, innsendingsid))

		assertTrue(message.isCaptured)
		assertEquals(personId, message.captured.getFodselsnummer())
		assertEquals(innsendingsid, message.captured.getGrupperingsId())
		assertTrue(message.captured.getTekst().contains(tittel))
		assertEquals(brukernotifikasjonPublisher?.tittelPrefixNySoknad + tittel, message.captured.getTekst())
		assertEquals(kafkaConfig.tjenesteUrl + kafkaConfig.gjenopptaSoknadsArbeid + innsendingsid, message.captured.getLink())
	}

	@Test
	fun `sjekk at Done melding blir publisert ved innsending av dokumentsoknad`() {
		val innsendingsid = "123456"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val personId = "12125912345"
		val tema = "PEN"
		val tittel = "Dokumentasjon av utdanning"
		val id = 2L

		val done = slot<Done>()

		every { kafkaPublisher.putApplicationDoneOnTopic(any(), capture(done)) } returns Unit

		brukernotifikasjonPublisher?.soknadStatusChange(lagDokumentSoknad(personId, skjemanr, spraak, tittel, tema, id, innsendingsid, SoknadsStatus.Innsendt))

		assertTrue(done.isCaptured)
		assertEquals(personId, done.captured.getFodselsnummer())
	}

	@Test
	fun `sjekk at Done og Oppgave meldinger blir publisert ved innsending av dokumentsoknad med vedlegg som skal ettersendes`() {
		val innsendingsid = "123456"
		val ettersendingsSoknadsId = "123457"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val personId = "12125912345"
		val tema = "PEN"
		val tittel = "Dokumentasjon av utdanning"
		val id = 3L

		val done = slot<Done>()
		val oppgave = slot<Oppgave>()

		every { kafkaPublisher.putApplicationDoneOnTopic(any(), capture(done)) } returns Unit
		every { kafkaPublisher.putApplicationTaskOnTopic(any(), capture(oppgave)) } returns Unit

		val soknad = lagDokumentSoknad(personId, skjemanr, spraak, tittel, tema, id, innsendingsid, SoknadsStatus.Innsendt,
			listOf(
				lagVedlegg(1L, "X1", "Vedlegg-X1", OpplastingsStatus.INNSENDT, false,"/litenPdf.pdf" ),
				lagVedlegg(2L, "X2", "Vedlegg-X2", OpplastingsStatus.SEND_SENERE, false)),
		)

		brukernotifikasjonPublisher?.soknadStatusChange(soknad)

		assertTrue(done.isCaptured)
		assertEquals(personId, done.captured.getFodselsnummer())

		val ettersending = lagDokumentSoknad(personId, skjemanr, spraak, tittel, tema, id, ettersendingsSoknadsId, SoknadsStatus.Opprettet,
			listOf(
				lagVedlegg(1L, "X1", "Vedlegg-X1", OpplastingsStatus.INNSENDT, false,"/litenPdf.pdf" ),
				lagVedlegg(2L, "X2", "Vedlegg-X2", OpplastingsStatus.IKKE_VALGT, false)), soknad.innsendingsId
		)

		brukernotifikasjonPublisher?.soknadStatusChange(ettersending)

		assertTrue(oppgave.isCaptured)
		assertEquals(personId, oppgave.captured.getFodselsnummer())
		assertEquals(innsendingsid, oppgave.captured.getGrupperingsId())
		assertTrue(oppgave.captured.getTekst().contains(tittel))
		assertEquals(brukernotifikasjonPublisher?.tittelPrefixEttersendelse + tittel, oppgave.captured.getTekst())
		assertEquals(kafkaConfig.tjenesteUrl + kafkaConfig.gjenopptaSoknadsArbeid + ettersendingsSoknadsId, oppgave.captured.getLink())
	}

	@Test
	fun `sjekk at Done melding blir publisert ved ettersending av dokumentsoknad`() {
		val innsendingsid = "123456"
		val ettersendingsSoknadsId = "123457"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val personId = "12125912345"
		val tema = "PEN"
		val tittel = "Dokumentasjon av utdanning"
		val id = 3L

		val nokler = mutableListOf<Nokkel>()
		val done = slot<Done>()

		every { kafkaPublisher.putApplicationDoneOnTopic(capture(nokler), capture(done)) } returns Unit   // Nokkel(appConfiguration.kafkaConfig.username, innsendingsid )

		brukernotifikasjonPublisher?.soknadStatusChange(
			lagDokumentSoknad(personId, skjemanr, spraak, tittel, tema, id, ettersendingsSoknadsId, SoknadsStatus.Innsendt,
				listOf(
					lagVedlegg(1L, "X1", "Vedlegg-X1", OpplastingsStatus.LASTET_OPP, false,"/litenPdf.pdf" ),
					lagVedlegg(2L, "X2", "Vedlegg-X2", OpplastingsStatus.LASTET_OPP, false, "/litenPdf.pdf")),
				innsendingsid))

		assertTrue(done.isCaptured)
		assertEquals(personId, done.captured.getFodselsnummer())
		assertEquals(ettersendingsSoknadsId, nokler[0].getEventId())

	}

	@Test
	fun `sjekk at Done melding blir publisert ved sletting av dokumentsoknad`() {
		val innsendingsid = "123456"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val personId = "12125912345"
		val tema = "PEN"
		val tittel = "Dokumentasjon av utdanning"
		val id = 2L

		val done = slot<Done>()

		every { kafkaPublisher.putApplicationDoneOnTopic(any(), capture(done)) } returns Unit

		brukernotifikasjonPublisher?.soknadStatusChange(lagDokumentSoknad(personId, skjemanr, spraak, tittel, tema, id, innsendingsid, SoknadsStatus.SlettetAvBruker))

		assertTrue(done.isCaptured)
		assertEquals(personId, done.captured.getFodselsnummer())
	}


}

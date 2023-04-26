package no.nav.soknad.innsending.service

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.BrukerNotifikasjonConfig
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.util.testpersonid
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootTest
@ActiveProfiles("spring")
@EnableTransactionManagement
class EttersendingServiceTest {

	@Autowired
	private val notifikasjonConfig: BrukerNotifikasjonConfig = BrukerNotifikasjonConfig()

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	private lateinit var skjemaService: SkjemaService

	@Autowired
	private lateinit var exceptionHelper: ExceptionHelper

	@Autowired
	private lateinit var soknadService: SoknadService

	@InjectMockKs
	private val sendTilPublisher = mockk<PublisherInterface>()

	private var brukernotifikasjonPublisher: BrukernotifikasjonPublisher? = null

	@BeforeEach
	fun setUp() {
		brukernotifikasjonPublisher = spyk(BrukernotifikasjonPublisher(notifikasjonConfig, sendTilPublisher))
	}

	private fun lagEttersendingService(): EttersendingService = EttersendingService(
		repo = repo,
		skjemaService = skjemaService,
		exceptionHelper = exceptionHelper,
		brukerNotifikasjon = brukernotifikasjonPublisher!!,
		innsenderMetrics = innsenderMetrics,
	)

	@Test
	fun `Skal sende brukernotifikasjon med erSystemGenerert=true hvis det mangler paakrevde vedlegg`() {
		// Gitt
		val brukerid = testpersonid
		val skjemanr = "NAV 55-00.60"
		val spraak = "nb_NO"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		val ettersendingService = lagEttersendingService()

		val message = slot<AddNotification>()
		every { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) } returns Unit

		// Når
		ettersendingService.sjekkOgOpprettEttersendingsSoknad(
			innsendtSoknadDto = dokumentSoknadDto,
			manglende = dokumentSoknadDto.vedleggsListe,
			soknadDtoInput = dokumentSoknadDto
		)

		// Så
		Assertions.assertNotNull(message.captured)
		Assertions.assertTrue(message.captured.soknadRef.erSystemGenerert == true)
	}

	@Test
	fun `Skal ikke opprette ettersendingssoknad hvis det ikke mangler paakrevde vedlegg`() {
		// Gitt
		val brukerid = testpersonid
		val skjemanr = "NAV 55-00.60"
		val spraak = "nb_NO"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		val ettersendingService = lagEttersendingService()
		val ettersendingServiceMock = spyk(ettersendingService)

		every { ettersendingServiceMock.opprettEttersendingsSoknad(any(), any()) } answers { callOriginal() }

		// Når
		ettersendingServiceMock.sjekkOgOpprettEttersendingsSoknad(
			innsendtSoknadDto = dokumentSoknadDto,
			manglende = emptyList(),
			soknadDtoInput = dokumentSoknadDto
		)

		// Så
		verify { ettersendingServiceMock.opprettEttersendingsSoknad(any(), any()) wasNot Called }
	}
}

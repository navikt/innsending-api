package no.nav.soknad.innsending.cleanup

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.service.*
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import kotlin.test.assertEquals

class FjernGamleSoknaderTest : ApplicationTest() {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var skjemaService: SkjemaService

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	private lateinit var vedleggService: VedleggService

	@Autowired
	private lateinit var ettersendingService: EttersendingService

	@Autowired
	private lateinit var filService: FilService

	@Autowired
	private lateinit var exceptionHelper: ExceptionHelper

	@InjectMockKs
	private val brukernotifikasjonPublisher = mockk<BrukernotifikasjonPublisher>()

	@InjectMockKs
	private val leaderSelectionUtility = mockk<LeaderSelectionUtility>()

	@InjectMockKs
	private val soknadsmottakerAPI = mockk<MottakerInterface>()

	@InjectMockKs
	private val fillagerAPI = mockk<FillagerInterface>()


	private fun lagSoknadService(): SoknadService = SoknadService(
		skjemaService = skjemaService,
		repo = repo,
		vedleggService = vedleggService,
		ettersendingService = ettersendingService,
		filService = filService,
		brukernotifikasjonPublisher = brukernotifikasjonPublisher,
		innsenderMetrics = innsenderMetrics,
		exceptionHelper = exceptionHelper
	)

	@BeforeEach
	fun init() {
	}

	private val defaultSkjemanr = "NAV 55-00.60"

	@Test
	fun testSlettingAvGammelIkkeInnsendtSoknad() {
		val soknadService = lagSoknadService()

		val soknader = mutableListOf<DokumentSoknadDto>()
		every { brukernotifikasjonPublisher.soknadStatusChange(capture(soknader)) } returns true
		every { leaderSelectionUtility.isLeader() } returns true
		every { soknadsmottakerAPI.sendInnSoknad(any(), any()) } returns Unit

		val spraak = "no"
		val tema = "BID"

		val gammelSoknadId = soknadService.opprettNySoknad(
			Hjelpemetoder.lagDokumentSoknad(
				brukerId = "12345678901", skjemanr = defaultSkjemanr, spraak = spraak, tittel = "En test",
				tema = tema, id = null, innsendingsid = null, soknadsStatus = SoknadsStatusDto.opprettet, vedleggsListe = null,
				ettersendingsId = null, OffsetDateTime.now().minusDays(DEFAULT_LEVETID_OPPRETTET_SOKNAD + 1)
			)
		).innsendingsId!!

		val nyereSoknadId = soknadService.opprettNySoknad(
			Hjelpemetoder.lagDokumentSoknad(
				brukerId = "12345678901", skjemanr = defaultSkjemanr, spraak = spraak, tittel = "En test",
				tema = tema, id = null, innsendingsid = null, soknadsStatus = SoknadsStatusDto.opprettet, vedleggsListe = null,
				ettersendingsId = null, OffsetDateTime.now().minusDays(DEFAULT_LEVETID_OPPRETTET_SOKNAD - 1)
			)
		).innsendingsId!!


		val initAntall = innsenderMetrics.operationsCounterGet(InnsenderOperation.SLETT.name, tema)
		val fjernGamleSoknader = FjernGamleSoknader(soknadService, leaderSelectionUtility)

		fjernGamleSoknader.fjernGamleIkkeInnsendteSoknader()

		val slettetSoknad = soknadService.hentSoknad(gammelSoknadId)
		assertTrue(slettetSoknad.status == SoknadsStatusDto.automatiskSlettet)
		assertTrue(soknader.any { it.innsendingsId == gammelSoknadId && it.status == SoknadsStatusDto.automatiskSlettet })
		assertEquals(1.0 + (initAntall ?: 0.0), innsenderMetrics.operationsCounterGet(InnsenderOperation.SLETT.name, tema))

		val beholdtSoknad = soknadService.hentSoknad(nyereSoknadId)
		assertTrue(beholdtSoknad.status == SoknadsStatusDto.opprettet)
		assertTrue(soknader.none { it.innsendingsId == nyereSoknadId && it.status == SoknadsStatusDto.automatiskSlettet })

	}

}

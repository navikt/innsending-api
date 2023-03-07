package no.nav.soknad.innsending.cleanup

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.skjema.SkjemaClient
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.repository.FilRepository
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.VedleggRepository
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD
import no.nav.soknad.innsending.utils.lagDokumentSoknad
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.time.OffsetDateTime
import kotlin.test.assertEquals

@SpringBootTest
@ActiveProfiles("spring")
@EnableTransactionManagement
class FjernGamleSoknaderTest {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var skjemaService: HentSkjemaDataConsumer

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@InjectMockKs
	private val brukernotifikasjonPublisher = mockk<BrukernotifikasjonPublisher>()

	@InjectMockKs
	private val hentSkjemaData = mockk<SkjemaClient>()

	@InjectMockKs
	private val fillagerAPI = mockk<FillagerInterface>()

	@InjectMockKs
	private val soknadsmottakerAPI = mockk<MottakerInterface>()

	@InjectMockKs
	private val pdlInterface = mockk<PdlInterface>()

	@InjectMockKs
	private val leaderSelectionUtility = mockk<LeaderSelectionUtility>()

	@Autowired
	private lateinit var restConfig: RestConfig


	private fun lagSoknadService(): SoknadService = SoknadService(
		skjemaService, repo, brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics, pdlInterface, restConfig)

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

		val spraak = "no"
		val tema = "BID"

		val gammelSoknadId = soknadService.opprettNySoknad(
			lagDokumentSoknad(brukerId = "12345678901", skjemanr = defaultSkjemanr, spraak = spraak, tittel = "En test",
				tema = tema, id = null, innsendingsid = null, soknadsStatus = SoknadsStatusDto.opprettet, vedleggsListe = null,
				ettersendingsId = null, OffsetDateTime.now().minusDays(DEFAULT_LEVETID_OPPRETTET_SOKNAD+1) ))

		val nyereSoknadId = soknadService.opprettNySoknad(
			lagDokumentSoknad(brukerId = "12345678901", skjemanr = defaultSkjemanr, spraak = spraak, tittel = "En test",
				tema = tema, id = null, innsendingsid = null, soknadsStatus = SoknadsStatusDto.opprettet, vedleggsListe = null,
				ettersendingsId = null, OffsetDateTime.now().minusDays(DEFAULT_LEVETID_OPPRETTET_SOKNAD-1) ))


		val initAntall = innsenderMetrics.operationsCounterGet(InnsenderOperation.SLETT.name, tema)
		val fjernGamleSoknader = FjernGamleSoknader(soknadService, leaderSelectionUtility)

		fjernGamleSoknader.fjernGamleIkkeInnsendteSoknader()

		val slettetSoknad = soknadService.hentSoknad(gammelSoknadId)
		assertTrue(slettetSoknad != null && slettetSoknad.status == SoknadsStatusDto.automatiskSlettet)
		assertTrue(soknader.any{ it.innsendingsId == gammelSoknadId && it.status == SoknadsStatusDto.automatiskSlettet })
		assertEquals(1.0 + (initAntall ?: 0.0) , innsenderMetrics.operationsCounterGet(InnsenderOperation.SLETT.name, tema))

		val beholdtSoknad = soknadService.hentSoknad(nyereSoknadId)
		assertTrue(beholdtSoknad != null && beholdtSoknad.status == SoknadsStatusDto.opprettet)
		assertTrue(soknader.none { it.innsendingsId == nyereSoknadId && it.status == SoknadsStatusDto.automatiskSlettet })

	}

}

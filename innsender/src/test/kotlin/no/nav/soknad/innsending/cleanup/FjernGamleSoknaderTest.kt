package no.nav.soknad.innsending.cleanup

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.skjema.SkjemaClient
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.repository.FilRepository
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.VedleggRepository
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.util.testpersonid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class FjernGamleSoknaderTest {
	@Autowired
	private lateinit var soknadRepository: SoknadRepository

	@Autowired
	private lateinit var vedleggRepository: VedleggRepository

	@Autowired
	private lateinit var filRepository: FilRepository

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


	@BeforeEach
	fun setup() {
		every { hentSkjemaData.hent() } returns skjemaService.initSkjemaDataFromDisk()
		every { brukernotifikasjonPublisher.soknadStatusChange(any()) } returns true
	}


	@AfterEach
	fun ryddOpp() {
		filRepository.deleteAll()
		vedleggRepository.deleteAll()
		soknadRepository.deleteAll()
	}

	@Test
	fun testAutomatiskSlettingAvGamleSoknader() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val brukerid = testpersonid
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"

		val dokumentSoknadDtoList = mutableListOf<DokumentSoknadDto>()
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak))
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak))

		soknadService.slettGamleIkkeInnsendteSoknader(1L)
		dokumentSoknadDtoList.forEach{ Assertions.assertEquals(soknadService.hentSoknad(it.id!!).status, SoknadsStatusDto.opprettet) }

		soknadService.slettGamleIkkeInnsendteSoknader(0L)
		dokumentSoknadDtoList.forEach{ Assertions.assertEquals(soknadService.hentSoknad(it.id!!).status, SoknadsStatusDto.automatiskSlettet) }
	}

}

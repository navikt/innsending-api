package no.nav.soknad.innsending.supervision

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.cleanup.LeaderSelection
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.domain.models.ConfigDbData
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.service.config.utils.toDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class NologinSupervisionTest {
	private var configService: ConfigService = mockk<ConfigService>()

	private val soknadRepository: SoknadRepository = mockk<SoknadRepository>()

	private var metrics: InnsenderMetrics = mockk<InnsenderMetrics>()

	private var leaderSelection: LeaderSelection = mockk<LeaderSelection>()

	private lateinit var nologinSupervision: NologinSupervision

	private val mainSwitchOff = configDto(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off")
	private val mainSwitchOn = configDto(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on")

	private val maxNumberOfSubmissions = 100L

	@BeforeEach
	fun setup() {
		clearAllMocks()
		every { leaderSelection.isLeader() } returns true
		every { metrics.setNologinMainSwitch(any()) } returns Unit
		every { configService.getConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_WINDOW_MINUTES) } returns configDto(
			ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_WINDOW_MINUTES,
			"60"
		)
		every { configService.getConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT) } returns configDto(
			ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT,
			maxNumberOfSubmissions.toString()
		)
		every { configService.setConfig(any(), any(), any()) } answers {
			configDto(firstArg(), secondArg())
		}
		nologinSupervision = NologinSupervision(
			soknadRepository = soknadRepository,
			configService = configService,
			metrics = metrics,
			leaderSelection = leaderSelection
		)
	}

	@Test
	fun `should skip run nologin supervision when main switch off`() {
		every { configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH) } returns mainSwitchOff
		nologinSupervision.supervise()

		verify(exactly = 0) { soknadRepository.countRecentlySubmitted(any(), any()) }
		verify(exactly = 1) { metrics.setNologinMainSwitch(0) }
	}

	@Test
	fun `should run nologin supervision when main switch on`() {
		every { configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH) } returns mainSwitchOn
		every { soknadRepository.countRecentlySubmitted(any(), any()) } returns (maxNumberOfSubmissions - 10)
		nologinSupervision.supervise()

		verify(exactly = 1) { soknadRepository.countRecentlySubmitted(any(), any()) }
		verify(exactly = 1) { metrics.setNologinMainSwitch(1) }
		verify(exactly = 0) { configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off", any()) }
	}

	@Test
	fun `should automatically disable nologin main switch`() {
		every { configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH) } returns mainSwitchOn
		every { soknadRepository.countRecentlySubmitted(any(), any()) } returns (maxNumberOfSubmissions + 10)
		nologinSupervision.supervise()

		verify(exactly = 1) { soknadRepository.countRecentlySubmitted(any(), any()) }
		verify(exactly = 1) { metrics.setNologinMainSwitch(0) }
		verify(exactly = 1) { configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off", any()) }
	}

	private fun configDto(definition: ConfigDefinition, value: String) = ConfigDbData(
		key = definition.key, value = value, createdAt = LocalDateTime.now()
	).toDto()

}

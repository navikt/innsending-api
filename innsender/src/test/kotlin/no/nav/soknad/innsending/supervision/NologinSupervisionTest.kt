package no.nav.soknad.innsending.supervision

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.mockk.verifySequence
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
	fun `should report main switch on without supervising when not leader`() {
		every { leaderSelection.isLeader() } returns false
		every { configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH) } returns mainSwitchOn

		nologinSupervision.supervise()

		verify(exactly = 1) { metrics.setNologinMainSwitch(1) }
		verify(exactly = 0) { soknadRepository.countRecentlySubmitted(any(), any()) }
		verify(exactly = 0) { configService.setConfig(any(), any(), any()) }
		verify(exactly = 0) { configService.getConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT) }
		verify(exactly = 0) { configService.getConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_WINDOW_MINUTES) }
	}

	@Test
	fun `should report main switch off when not leader`() {
		every { leaderSelection.isLeader() } returns false
		every { configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH) } returns mainSwitchOff

		nologinSupervision.supervise()

		verify(exactly = 1) { metrics.setNologinMainSwitch(0) }
		verify(exactly = 0) { soknadRepository.countRecentlySubmitted(any(), any()) }
		verify(exactly = 0) { configService.setConfig(any(), any(), any()) }
	}

	@Test
	fun `should update main switch from on to off when not leader`() {
		every { leaderSelection.isLeader() } returns false
		every { configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH) } returnsMany listOf(mainSwitchOn, mainSwitchOff)

		nologinSupervision.supervise()
		nologinSupervision.supervise()

		verifySequence {
			metrics.setNologinMainSwitch(1)
			metrics.setNologinMainSwitch(0)
		}
		verify(exactly = 0) { soknadRepository.countRecentlySubmitted(any(), any()) }
		verify(exactly = 0) { configService.setConfig(any(), any(), any()) }
	}

	@Test
	fun `should report main switch even when leader selection fails`() {
		every { leaderSelection.isLeader() } throws IllegalStateException("Leader selection unavailable")
		every { configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH) } returns mainSwitchOn

		nologinSupervision.supervise()

		verify(exactly = 1) { metrics.setNologinMainSwitch(1) }
		verify(exactly = 0) { soknadRepository.countRecentlySubmitted(any(), any()) }
		verify(exactly = 0) { configService.setConfig(any(), any(), any()) }
	}

	@Test
	fun `should not count recently submitted nologin application when main switch off`() {
		every { configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH) } returns mainSwitchOff
		nologinSupervision.supervise()

		verify(exactly = 0) { soknadRepository.countRecentlySubmitted(any(), any()) }
		verify(exactly = 1) { metrics.setNologinMainSwitch(0) }
	}

	@Test
	fun `should count recently submitted nologin applications when main switch on`() {
		every { configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH) } returns mainSwitchOn
		every { soknadRepository.countRecentlySubmitted(any(), any()) } returns (maxNumberOfSubmissions - 10)
		nologinSupervision.supervise()

		verify(exactly = 1) { soknadRepository.countRecentlySubmitted(any(), any()) }
		verify(exactly = 1) { metrics.setNologinMainSwitch(1) }
		verify(exactly = 0) { configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off", any()) }
	}

	@Test
	fun `should automatically disable nologin main switch when threshold is exceeded`() {
		every { configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH) } returns mainSwitchOn
		every { soknadRepository.countRecentlySubmitted(any(), any()) } returns (maxNumberOfSubmissions + 10)
		nologinSupervision.supervise()

		verify(exactly = 1) { soknadRepository.countRecentlySubmitted(any(), any()) }
		verify(exactly = 1) { metrics.setNologinMainSwitch(0) }
		verify(exactly = 1) { configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off", any()) }
		verifyOrder {
			metrics.setNologinMainSwitch(1)
			configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off", "system")
			metrics.setNologinMainSwitch(0)
		}
	}

	private fun configDto(definition: ConfigDefinition, value: String) = ConfigDbData(
		key = definition.key, value = value, createdAt = LocalDateTime.now()
	).toDto()

}

package no.nav.soknad.innsending.cleanup

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.soknad.innsending.ApplicationTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class LeaderSelectionTest : ApplicationTest() {

	@OptIn(ExperimentalSerializationApi::class)
	val format = Json { explicitNulls = false; ignoreUnknownKeys = true }

	@Test
	fun testLeaderSelection() {
		val leaderElection = LeaderElection("localhost", LocalDateTime.now().toString())
		val jsonString = format.encodeToString(leaderElection)
		System.setProperty("ELECTOR_PATH", "localhost")

		val leaderSelector = mockk<LeaderSelectionUtility>()
		every { leaderSelector.fetchLeaderSelection() } returns jsonString
		every { leaderSelector.logger.warn(any()) } returns Unit
		every { leaderSelector.logger.info(any()) } returns Unit
		every { leaderSelector.format.decodeFromString<LeaderElection>(any()) } returns leaderElection
		every { leaderSelector.isLeader() } answers { callOriginal() }

		leaderSelector.isLeader()

	}

}

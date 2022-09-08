package no.nav.soknad.innsending.cleanup

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class LeaderSelectionTest {

	@OptIn(ExperimentalSerializationApi::class)
	val format = Json { explicitNulls = false; ignoreUnknownKeys = true }

	@BeforeEach
	fun setup() {
	}

	@AfterEach
	fun ryddOpp() {
	}

	@Test
	fun testLeaderSelection() {
		val leaderElection = LeaderElection("localhost", LocalDateTime.now().toString())
		val jsonString = format.encodeToString(leaderElection)
		System.setProperty("ELECTOR_PATH", "localhost")

		val leaderSelector = mockk<LeaderSelectionUtility>()
		every {leaderSelector.fetchLeaderSelection()} returns jsonString
		every {leaderSelector.logger.warn(any())} returns Unit
		every {leaderSelector.logger.info(any())} returns Unit
		every {leaderSelector.format.decodeFromString<LeaderElection>(any())} returns leaderElection
		every {leaderSelector.isLeader()} answers { callOriginal() }

		leaderSelector.isLeader()

	}

}

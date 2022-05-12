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

class FjernGamleSoknaderTest {

	@OptIn(ExperimentalSerializationApi::class)
	val format = Json { explicitNulls = false }

	@BeforeEach
	fun setup() {
	}

	@AfterEach
	fun ryddOpp() {
	}

	@Test
	fun testLeaderSelection() {
		val leaderElection = FjernGamleSoknader.LeaderElection("localhost", LocalDateTime.now().toString())
		val jsonString = format.encodeToString(leaderElection)

		val fjernGamleSoknader = mockk<FjernGamleSoknader>()
		every {fjernGamleSoknader.fetchLeaderSelection()} returns jsonString
		every {fjernGamleSoknader.logger.warn(any())} returns Unit
		every {fjernGamleSoknader.logger.info(any())} returns Unit
		every {fjernGamleSoknader.format.decodeFromString<FjernGamleSoknader.LeaderElection>(any())} returns leaderElection
		every {fjernGamleSoknader.isLeader()} answers { callOriginal() }

		fjernGamleSoknader.isLeader()

	}

}

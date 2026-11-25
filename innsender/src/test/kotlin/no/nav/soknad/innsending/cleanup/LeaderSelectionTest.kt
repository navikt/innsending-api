package no.nav.soknad.innsending.cleanup

import io.mockk.every
import io.mockk.spyk
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.net.InetAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeaderSelectionTest {

	@OptIn(ExperimentalSerializationApi::class)
	private val format = Json { explicitNulls = false; ignoreUnknownKeys = true }

	private fun electorResponse(leader: String) = format.encodeToString(LeaderElection(leader))

	@Test
	fun `this pod is leader when the elector names its host`() {
		val leaderSelection = spyk(LeaderSelectionUtility())
		every { leaderSelection.fetchLeaderSelection() } returns electorResponse(InetAddress.getLocalHost().hostName.uppercase())

		assertTrue(leaderSelection.isLeader())
	}

	@Test
	fun `this pod is not leader when the elector names another host`() {
		val leaderSelection = spyk(LeaderSelectionUtility())
		every { leaderSelection.fetchLeaderSelection() } returns electorResponse("another-pod")

		assertFalse(leaderSelection.isLeader())
	}
}

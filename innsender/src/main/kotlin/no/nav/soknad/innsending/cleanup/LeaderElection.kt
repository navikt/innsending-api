package no.nav.soknad.innsending.cleanup

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class LeaderElection(
	val name: String,
	val last_update: String? = LocalDateTime.now().toString()
)


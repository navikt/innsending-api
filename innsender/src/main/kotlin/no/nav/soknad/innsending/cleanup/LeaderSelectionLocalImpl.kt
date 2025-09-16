package no.nav.soknad.innsending.cleanup

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Profile("local | docker")
@Component
class LeaderSelectionLocalImpl: LeaderSelection {
		override fun isLeader(): Boolean = true
}

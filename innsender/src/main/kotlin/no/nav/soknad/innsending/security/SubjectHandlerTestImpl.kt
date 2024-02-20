package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.util.testpersonid
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local | docker")
class SubjectHandlerTestImpl : SubjectHandlerInterface {
	private var token = "token"

	override fun getUserIdFromToken(): String {
		return testpersonid
	}

	override fun getToken(): String {
		return this.token
	}

	override fun getConsumerId(): String {
		return "StaticConsumerId"
	}

	override fun getClientId(): String {
		return "StaticClientId"
	}
}

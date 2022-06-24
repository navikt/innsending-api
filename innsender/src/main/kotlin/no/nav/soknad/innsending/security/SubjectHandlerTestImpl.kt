package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.util.testpersonid
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("spring | default | docker")
class SubjectHandlerTestImpl: SubjectHandlerInterface {
	private val DEFAULT_TOKEN = "token"
	private var token = DEFAULT_TOKEN

	override fun getUserIdFromToken(): String {
		return testpersonid
	}

	override fun getToken(): String {
		return this.token
	}

	override fun getConsumerId(): String {
		return "StaticConsumerId"
	}

}

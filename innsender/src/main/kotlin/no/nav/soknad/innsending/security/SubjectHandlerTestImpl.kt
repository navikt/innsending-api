package no.nav.soknad.innsending.security

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("test | spring | default | docker")
class SubjectHandlerTestImpl: SubjectHandlerInterface {
	private val DEFAULT_USER = "02097225454"
	private val DEFAULT_TOKEN = "token"
	private var user = DEFAULT_USER
	private var token = DEFAULT_TOKEN

	override fun getUserIdFromToken(): String {
		return this.user
	}

	override fun getToken(): String {
		return this.token
	}

	override fun getConsumerId(): String {
		return "StaticConsumerId"
	}

	fun setUser(user: String) {
		this.user = user
	}

	fun setFakeToken(fakeToken: String) {
		this.token = fakeToken
	}

	fun reset() {
		this.user = DEFAULT_USER
		this.token = DEFAULT_TOKEN
	}
}

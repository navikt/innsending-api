package no.nav.soknad.innsending.utils

import com.nimbusds.jose.JOSEObjectType
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.MockLoginController
import org.springframework.stereotype.Component

@Component
class TokenGenerator(
	private val mockOAuth2Server: MockOAuth2Server
) {
	private val tokenx = "tokenx"
	private val subject = "12345678901"
	private val audience = "aud-localhost"
	private val expiry = 2 * 3600

	fun lagTokenXToken(fnr: String? = null): String {
		val pid = fnr ?: subject
		return mockOAuth2Server.issueToken(
			tokenx,
			MockLoginController::class.java.simpleName,
			DefaultOAuth2TokenCallback(
				tokenx,
				subject,
				JOSEObjectType.JWT.type,
				listOf(audience),
				mapOf("acr" to "Level4", "pid" to pid),
				expiry.toLong()
			)
		).serialize()
	}
}
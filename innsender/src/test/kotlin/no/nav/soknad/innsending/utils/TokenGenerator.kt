package no.nav.soknad.innsending.utils

import com.nimbusds.jose.JOSEObjectType
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.MockLoginController
import no.nav.soknad.innsending.util.Constants.AZURE

class TokenGenerator(
	private val mockOAuth2Server: MockOAuth2Server,
) {
	companion object {
		val subject = "12345678901"
	}

	private val tokenx = "tokenx"
	private val audience = "aud-localhost"
	private val expiry = 2 * 3600L

	fun lagTokenXToken(fnr: String? = null): String {
		val pid = fnr ?: subject
		return mockOAuth2Server.issueToken(
			issuerId = tokenx,
			clientId = "application",
			tokenCallback = DefaultOAuth2TokenCallback(
				issuerId = tokenx,
				subject = pid,
				typeHeader = JOSEObjectType.JWT.type,
				audience = listOf(audience),
				claims = mapOf("acr" to "idporten-loa-high", "pid" to pid),
				expiry = expiry
			)
		).serialize()
	}

	fun lagAzureToken(fnr: String? = null): String {
		val pid = fnr ?: subject
		val issuerId = AZURE
		val oAuth2TokenCallback = DefaultOAuth2TokenCallback(
			issuerId = issuerId,
			subject = pid,
			typeHeader = JOSEObjectType.JWT.type,
			audience =listOf(audience),
			claims = mapOf("scp=defaultaccess" to "oppgave-initiering"),
			expiry = expiry
		)
		return mockOAuth2Server.issueToken(
			issuerId = issuerId,
			clientId = MockLoginController::class.java.simpleName,
			tokenCallback = oAuth2TokenCallback
		).serialize()

	}

}

package no.nav.soknad.innsending.utils

import com.nimbusds.jose.JOSEObjectType
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.MockLoginController
import no.nav.soknad.innsending.util.Constants.AZURE
import org.springframework.security.oauth2.jwt.Jwt
import com.nimbusds.jwt.SignedJWT
import java.time.Instant


class TokenGenerator(
	private val mockOAuth2Server: MockOAuth2Server,
) {
	companion object {
		val subject = "12345678901"
	}

	private val tokenx = "tokenx"
	private val audience = "aud-localhost"
	private val expiry = 2 * 3600L


	fun createMockJwt(issuer: String, fnr: String = subject, aud: String = audience, navIdent: String? = null): Jwt {
		return Jwt.withTokenValue("mock-token")
			.header("kid", "azuread")
			.header("typ", "JWT")
			.header("alg", "RS256")
			.claim("client_id", "application")
			.claim("roles", listOf("nologin-access"))
			.claim("iss", issuer)
			.claim("aud", aud)
			.claim("sub", fnr)
			.claim(if (navIdent != null) "NAVident" else "pid", navIdent ?: fnr)
			.claim("pid", subject)
			.build()
	}

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

	fun lagTokenXTokenAndJwt(fnr: String? = null): Pair<String, Jwt> {
		val pid = fnr ?: subject
		val token = mockOAuth2Server.issueToken(
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
		return token to toSpringJwt(token)
	}

	fun lagAzureToken(fnr: String? = null): String {
		val pid = fnr ?: subject
		val issuerId = AZURE
		val oAuth2TokenCallback = DefaultOAuth2TokenCallback(
			issuerId = issuerId,
			subject = pid,
			typeHeader = JOSEObjectType.JWT.type,
			audience =listOf(audience),
			claims = mapOf("scp=defaultaccess" to "oppgave-initiering", "roles" to "unauthenticated-file-storage-access"),
			expiry = expiry
		)
		return mockOAuth2Server.issueToken(
			issuerId = issuerId,
			clientId = MockLoginController::class.java.simpleName,
			tokenCallback = oAuth2TokenCallback
		).serialize()
	}

	fun lagAzureM2MToken(roles: List<String> = emptyList(), issuer: String = AZURE): String {
		val issuerId = issuer
		val oAuth2TokenCallback = DefaultOAuth2TokenCallback(
			issuerId = issuerId,
			typeHeader = JOSEObjectType.JWT.type,
			audience = listOf(audience),
			claims = buildMap {
				put("roles", listOf("access_as_application").plus(roles))
			},
			expiry = expiry
		)
		return mockOAuth2Server.issueToken(
			issuerId = issuer,
			clientId = MockLoginController::class.java.simpleName,
			tokenCallback = oAuth2TokenCallback
		).serialize()
	}

	fun lagAzureM2MTokenAndJwt(roles: List<String> = emptyList(), issuer: String = AZURE): Pair<String, Jwt> {
		val issuerId = issuer
		val oAuth2TokenCallback = DefaultOAuth2TokenCallback(
			issuerId = issuerId,
			typeHeader = JOSEObjectType.JWT.type,
			audience = listOf(audience),
			claims = buildMap {
				put("roles", listOf("access_as_application").plus(roles))
			},
			expiry = expiry
		)
		val token = mockOAuth2Server.issueToken(
			issuerId = issuer,
			clientId = MockLoginController::class.java.simpleName,
			tokenCallback = oAuth2TokenCallback
		).serialize()
		return token to toSpringJwt(token)
	}

	fun lagAzureOBOToken(scopes: String? = null, navIdent: String? = null): String {
		val issuerId = AZURE
		val oAuth2TokenCallback = DefaultOAuth2TokenCallback(
			issuerId = issuerId,
			typeHeader = JOSEObjectType.JWT.type,
			audience = listOf(audience),
			claims = buildMap {
				put("name", "Ola Nordmann")
				put("preferred_username", "Ola.Nordmann@test.no")
				put("azp", "consumer-app-id")
				put("azp_name", "dev-gcp.namespace.consumer-app-name")
				put("scp", "${scopes?.let { "$it " } ?: ""}defaultaccess")
				if (navIdent != null) {
					put("NAVident", navIdent)
				}
			},
			expiry = expiry
		)
		return mockOAuth2Server.issueToken(
			issuerId = issuerId,
			clientId = MockLoginController::class.java.simpleName,
			tokenCallback = oAuth2TokenCallback
		).serialize()

	}

	fun lagAzureOBOTokenAndJwt(scopes: String? = null, navIdent: String? = null): Pair<String, Jwt> {
		val issuerId = AZURE
		val oAuth2TokenCallback = DefaultOAuth2TokenCallback(
			issuerId = issuerId,
			typeHeader = JOSEObjectType.JWT.type,
			audience = listOf(audience),
			claims = buildMap {
				put("name", "Ola Nordmann")
				put("preferred_username", "Ola.Nordmann@test.no")
				put("azp", "consumer-app-id")
				put("azp_name", "dev-gcp.namespace.consumer-app-name")
				put("scp", "${scopes?.let { "$it " } ?: ""}defaultaccess")
				if (navIdent != null) {
					put("NAVident", navIdent)
				}
			},
			expiry = expiry
		)
		return mockOAuth2Server.issueToken(
			issuerId = issuerId,
			clientId = MockLoginController::class.java.simpleName,
			tokenCallback = oAuth2TokenCallback
		).serialize().let { it to toSpringJwt(it) }

	}

	fun toSpringJwt(string: String): Jwt {
		val signedJwt = SignedJWT.parse(string)
		val claims = signedJwt.jwtClaimsSet

		return Jwt.withTokenValue(string)
			.headers { h -> h.putAll(signedJwt.header.toJSONObject()) }
			.claims { c -> c.putAll(claims.claims) }
			.issuedAt(claims.issueTime?.toInstant() ?: Instant.EPOCH)
			.expiresAt(claims.expirationTime?.toInstant() ?: Instant.EPOCH)
			.build()
	}
}

package no.nav.soknad.innsending.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.consumerapis.token.IntrospectionResult
import no.nav.soknad.innsending.consumerapis.token.TokenIntrospector
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Stand-in for introspeksjon i Texas i tester, mot MockOAuth2Server (#461, steg 3i og 3j).
 *
 * Følger reglene Texas bruker: RS256-signatur med en nøkkel fra utstederens JWKS, riktig issuer og audience,
 * exp i framtiden og iat til stede. Texas krever ikke sub. Svaret har claimene som JSON-typer, slik Texas gir dem.
 * Om ekte Texas oppfører seg likt i dev og prod, viser skyggemodus i steg 3h.
 */
class FakeTokenIntrospector(
	private val server: MockOAuth2Server,
	/** Audience per issuer-ID i MockOAuth2Server, som accepted_audience for hver utsteder */
	private val audience: Map<String, String>,
) : TokenIntrospector {

	private val keys = ConcurrentHashMap<String, JWKSet>()

	/** Lar en test bestemme svaret, for eksempel for å simulere at Texas er nede. Nullstilles etter testen. */
	@Volatile
	var stub: ((issuer: TokenIssuer, token: String) -> IntrospectionResult)? = null

	override fun introspect(issuer: TokenIssuer, token: String): IntrospectionResult {
		stub?.let { return it(issuer, token) }
		val issuerId = when (issuer) {
			TokenIssuer.TOKENX -> "tokenx"
			TokenIssuer.AZURE -> "azuread"
		}
		return if (isValid(issuerId, token)) {
			IntrospectionResult(true, SignedJWT.parse(token).jwtClaimsSet.toJSONObject())
		} else {
			IntrospectionResult(false, emptyMap())
		}
	}

	private fun isValid(issuerId: String, token: String): Boolean {
		val jwt = runCatching { SignedJWT.parse(token) }.getOrNull() ?: return false
		if (jwt.header.algorithm != JWSAlgorithm.RS256) return false
		val key = keys.computeIfAbsent(issuerId) { JWKSet.load(server.jwksUrl(it).toUrl()) }
			.getKeyByKeyId(jwt.header.keyID) as? RSAKey ?: return false
		if (!runCatching { jwt.verify(RSASSAVerifier(key)) }.getOrDefault(false)) return false

		val claims = jwt.jwtClaimsSet
		val now = Date()
		return claims.issuer == server.issuerUrl(issuerId).toString() &&
			audience.getValue(issuerId) in claims.audience.orEmpty() &&
			claims.expirationTime?.after(now) == true &&
			claims.issueTime != null &&
			(claims.notBeforeTime == null || !claims.notBeforeTime.after(now))
	}
}

@Configuration
@Profile("test")
class FakeTokenIntrospectorConfig {

	@Bean
	@Primary
	fun fakeTokenIntrospector(
		server: MockOAuth2Server,
		@Value("\${fake-texas.audience.tokenx}") tokenxAudience: String,
		@Value("\${fake-texas.audience.azure}") azureAudience: String,
	): FakeTokenIntrospector = FakeTokenIntrospector(server, mapOf("tokenx" to tokenxAudience, "azuread" to azureAudience))
}

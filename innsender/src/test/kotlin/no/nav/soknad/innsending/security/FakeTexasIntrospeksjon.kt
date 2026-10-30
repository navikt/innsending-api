package no.nav.soknad.innsending.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjon
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjonKlient
import no.nav.soknad.innsending.consumerapis.texas.TokenIntrospeksjon
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
class FakeTexasIntrospeksjon(
	private val server: MockOAuth2Server,
	/** Audience per issuer-ID i MockOAuth2Server, som accepted_audience for hver utsteder */
	private val audience: Map<String, String>,
) : TokenIntrospeksjon {

	private val nøkler = ConcurrentHashMap<String, JWKSet>()

	/** Lar en test bestemme svaret, for eksempel for å simulere at Texas er nede. Nullstilles etter testen. */
	@Volatile
	var overstyring: ((identityProvider: String, token: String) -> TexasIntrospeksjon)? = null

	override fun introspekter(identityProvider: String, token: String): TexasIntrospeksjon {
		overstyring?.let { return it(identityProvider, token) }
		val issuerId = when (identityProvider) {
			TexasIntrospeksjonKlient.TOKENX -> "tokenx"
			TexasIntrospeksjonKlient.ENTRA_ID -> "azuread"
			else -> error("Ukjent identity_provider $identityProvider")
		}
		return if (gyldig(issuerId, token)) {
			TexasIntrospeksjon(true, SignedJWT.parse(token).jwtClaimsSet.toJSONObject())
		} else {
			TexasIntrospeksjon(false, emptyMap())
		}
	}

	private fun gyldig(issuerId: String, token: String): Boolean {
		val jwt = runCatching { SignedJWT.parse(token) }.getOrNull() ?: return false
		if (jwt.header.algorithm != JWSAlgorithm.RS256) return false
		val nøkkel = nøkler.computeIfAbsent(issuerId) { JWKSet.load(server.jwksUrl(it).toUrl()) }
			.getKeyByKeyId(jwt.header.keyID) as? RSAKey ?: return false
		if (!runCatching { jwt.verify(RSASSAVerifier(nøkkel)) }.getOrDefault(false)) return false

		val claims = jwt.jwtClaimsSet
		val nå = Date()
		return claims.issuer == server.issuerUrl(issuerId).toString() &&
			audience.getValue(issuerId) in claims.audience.orEmpty() &&
			claims.expirationTime?.after(nå) == true &&
			claims.issueTime != null &&
			(claims.notBeforeTime == null || !claims.notBeforeTime.after(nå))
	}
}

@Configuration
@Profile("test")
class FakeTexasIntrospeksjonConfig {

	@Bean
	@Primary
	fun fakeTexasIntrospeksjon(
		server: MockOAuth2Server,
		@Value("\${fake-texas.audience.tokenx}") tokenxAudience: String,
		@Value("\${fake-texas.audience.azure}") azureAudience: String,
	): FakeTexasIntrospeksjon = FakeTexasIntrospeksjon(server, mapOf("tokenx" to tokenxAudience, "azuread" to azureAudience))
}

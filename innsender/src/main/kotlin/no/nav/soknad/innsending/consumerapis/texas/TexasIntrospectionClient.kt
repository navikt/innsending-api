package no.nav.soknad.innsending.consumerapis.texas

import no.nav.soknad.innsending.consumerapis.token.IntrospectionResult
import no.nav.soknad.innsending.consumerapis.token.TokenIntrospector
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import no.nav.soknad.innsending.security.TokenIssuer
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/**
 * Validerer innkommende token hos Texas-sidecaren via NAIS_TOKEN_INTROSPECTION_ENDPOINT (#461).
 *
 * Texas svarer `active: true` med claimene når tokenet er gyldig for appen, og `active: false` med en feilmelding
 * ellers. Feilmeldingen tas ikke vare på, fordi vi ikke kontrollerer hva den inneholder. Feil i kallet blir
 * [TokenServiceUnavailableException] uten respons-body og uten cause, slik at tokenet aldri havner i logger.
 * Ingen retry i appen.
 */
class TexasIntrospectionClient(
	private val restClient: RestClient,
	private val introspectionEndpoint: String,
) : TokenIntrospector {

	override fun introspect(issuer: TokenIssuer, token: String): IntrospectionResult {
		val identityProvider = identityProvider(issuer)
		val description = "identity_provider=$identityProvider"
		val response = try {
			restClient.post()
				.uri(introspectionEndpoint)
				.contentType(MediaType.APPLICATION_JSON)
				.body(mapOf("identity_provider" to identityProvider, "token" to token))
				.retrieve()
				.body(Map::class.java)
		} catch (e: RestClientResponseException) {
			throw TokenServiceUnavailableException("Texas svarte ${e.statusCode.value()} på introspeksjon for $description")
		} catch (e: ResourceAccessException) {
			throw TokenServiceUnavailableException("Fikk ikke kontakt med Texas for introspeksjon $description (${e.javaClass.simpleName})")
		} catch (e: RestClientException) {
			throw TokenServiceUnavailableException("Ugyldig svar fra Texas på introspeksjon for $description (${e.javaClass.simpleName})")
		} ?: throw TokenServiceUnavailableException("Tomt svar fra Texas på introspeksjon for $description")

		val claims = response.entries.associate { (name, value) -> name.toString() to value }
		return when (claims["active"]) {
			true -> IntrospectionResult(active = true, claims = claims - setOf("active", "error"))
			false -> IntrospectionResult(active = false, claims = emptyMap())
			else -> throw TokenServiceUnavailableException("Svaret fra Texas manglet active for $description")
		}
	}

	// Texas sitt navn på utstederen
	private fun identityProvider(issuer: TokenIssuer) = when (issuer) {
		TokenIssuer.TOKENX -> "tokenx"
		TokenIssuer.AZURE -> "entra_id"
	}
}

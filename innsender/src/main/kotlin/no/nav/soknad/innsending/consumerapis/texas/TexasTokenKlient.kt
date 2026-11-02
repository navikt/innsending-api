package no.nav.soknad.innsending.consumerapis.texas

import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/**
 * Henter token fra Texas-sidecaren:
 * - maskintoken (Entra ID client credentials) via NAIS_TOKEN_ENDPOINT
 * - TokenX-utveksling av innbyggerens token via NAIS_TOKEN_EXCHANGE_ENDPOINT
 *
 * Texas cacher tokenene selv, så klienten har ingen egen cache og prøver ikke på nytt.
 * Feil blir [TexasUnavailableException] uten respons-body og uten cause, slik at verken
 * innbyggerens token eller tokenet fra Texas kan havne i logger gjennom en melding eller stacktrace.
 */
class TexasTokenKlient(
	private val restClient: RestClient,
	private val tokenEndpoint: String,
	private val exchangeEndpoint: String,
) {

	fun hentMaskinToken(target: String): String =
		hentToken(tokenEndpoint, ENTRA_ID, target, mapOf("identity_provider" to ENTRA_ID, "target" to target))

	fun hentBrukerToken(target: String, brukerToken: String): String =
		hentToken(
			exchangeEndpoint, TOKENX, target,
			mapOf("identity_provider" to TOKENX, "target" to target, "user_token" to brukerToken)
		)

	private fun hentToken(endpoint: String, identityProvider: String, target: String, body: Map<String, String>): String {
		val beskrivelse = "identity_provider=$identityProvider target=$target"
		val respons = try {
			restClient.post()
				.uri(endpoint)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(Map::class.java)
		} catch (e: RestClientResponseException) {
			throw TexasUnavailableException("Texas svarte ${e.statusCode.value()} for $beskrivelse")
		} catch (e: ResourceAccessException) {
			throw TexasUnavailableException("Fikk ikke kontakt med Texas for $beskrivelse (${e.javaClass.simpleName})")
		} catch (e: RestClientException) {
			throw TexasUnavailableException("Ugyldig svar fra Texas for $beskrivelse (${e.javaClass.simpleName})")
		}

		val token = respons?.get("access_token")
		if (token !is String || token.isBlank()) {
			throw TexasUnavailableException("Svaret fra Texas manglet access_token for $beskrivelse")
		}
		return token
	}

	private companion object {
		const val ENTRA_ID = "entra_id"
		const val TOKENX = "tokenx"
	}
}

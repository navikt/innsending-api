package no.nav.soknad.innsending.consumerapis.texas

import no.nav.soknad.innsending.consumerapis.token.AccessTokenProvider
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/**
 * Henter maskintoken (Entra ID client credentials) fra Texas-sidecaren via NAIS_TOKEN_ENDPOINT.
 *
 * Texas cacher tokenene selv, så klienten har ingen egen cache og prøver ikke på nytt.
 * Feil blir [TokenServiceUnavailableException] uten respons-body og uten cause, slik at tokenet aldri havner i logger.
 */
class TexasTokenClient(private val restClient: RestClient) : AccessTokenProvider {

	override fun getMachineToken(target: String): String {
		val response = try {
			restClient.post()
				.contentType(MediaType.APPLICATION_JSON)
				.body(mapOf("identity_provider" to ENTRA_ID, "target" to target))
				.retrieve()
				.body(Map::class.java)
		} catch (e: RestClientResponseException) {
			throw TokenServiceUnavailableException("Texas svarte ${e.statusCode.value()} for identity_provider=$ENTRA_ID target=$target")
		} catch (e: ResourceAccessException) {
			throw TokenServiceUnavailableException("Fikk ikke kontakt med Texas for identity_provider=$ENTRA_ID target=$target (${e.javaClass.simpleName})")
		} catch (e: RestClientException) {
			throw TokenServiceUnavailableException("Ugyldig svar fra Texas for identity_provider=$ENTRA_ID target=$target (${e.javaClass.simpleName})")
		}

		val token = response?.get("access_token")
		if (token !is String || token.isBlank()) {
			throw TokenServiceUnavailableException("Svaret fra Texas manglet access_token for identity_provider=$ENTRA_ID target=$target")
		}
		return token
	}

	private companion object {
		const val ENTRA_ID = "entra_id"
	}
}

package no.nav.soknad.innsending.consumerapis.texas

import no.nav.soknad.innsending.consumerapis.token.AccessTokenProvider
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.time.Duration

/**
 * Henter token fra Texas-sidecaren:
 * - maskintoken (Entra ID client credentials) via NAIS_TOKEN_ENDPOINT
 * - TokenX-utveksling av innbyggerens token via NAIS_TOKEN_EXCHANGE_ENDPOINT
 *
 * Texas cacher tokenene selv, så klienten har ingen egen cache. Ved forbigående feil prøver klienten én gang til
 * (se [TexasRetry]).
 * Feil blir [TokenServiceUnavailableException] uten respons-body og uten cause, slik at verken
 * innbyggerens token eller tokenet fra Texas kan havne i logger gjennom en melding eller stacktrace.
 */
class TexasTokenClient(
	private val restClient: RestClient,
	private val tokenEndpoint: String,
	private val exchangeEndpoint: String,
	retryWait: Duration = TexasRetry.DEFAULT_WAIT,
) : AccessTokenProvider {

	private val retry = TexasRetry.create("tokenkall", retryWait)

	override fun getMachineToken(target: String): String =
		fetchToken(tokenEndpoint, ENTRA_ID, target, mapOf("identity_provider" to ENTRA_ID, "target" to target))

	override fun exchangeUserToken(target: String, userToken: String): String =
		fetchToken(
			exchangeEndpoint, TOKENX, target,
			mapOf("identity_provider" to TOKENX, "target" to target, "user_token" to userToken)
		)

	private fun fetchToken(endpoint: String, identityProvider: String, target: String, body: Map<String, String>): String {
		val description = "identity_provider=$identityProvider target=$target"
		val response = try {
			retry.executeSupplier {
				restClient.post()
					.uri(endpoint)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(Map::class.java)
			}
		} catch (e: RestClientResponseException) {
			throw TokenServiceUnavailableException("Texas svarte ${e.statusCode.value()} for $description")
		} catch (e: ResourceAccessException) {
			throw TokenServiceUnavailableException("Fikk ikke kontakt med Texas for $description (${e.javaClass.simpleName})")
		} catch (e: RestClientException) {
			throw TokenServiceUnavailableException("Ugyldig svar fra Texas for $description (${e.javaClass.simpleName})")
		}

		val token = response?.get("access_token")
		if (token !is String || token.isBlank()) {
			throw TokenServiceUnavailableException("Svaret fra Texas manglet access_token for $description")
		}
		return token
	}

	private companion object {
		const val ENTRA_ID = "entra_id"
		const val TOKENX = "tokenx"
	}
}

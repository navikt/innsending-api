package no.nav.soknad.innsending.consumerapis.texas

import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/**
 * Validerer innkommende token hos Texas-sidecaren via NAIS_TOKEN_INTROSPECTION_ENDPOINT (#461).
 *
 * Texas svarer `active: true` med claimene når tokenet er gyldig for appen, og `active: false` med en feilmelding
 * ellers. Feilmeldingen tas ikke vare på, fordi vi ikke kontrollerer hva den inneholder. Feil i kallet blir [TexasUnavailableException] uten respons-body og uten cause, slik at tokenet aldri havner
 * i logger. Ingen retry i appen.
 */
class TexasIntrospeksjonKlient(
	private val restClient: RestClient,
	private val introspectionEndpoint: String,
) {

	fun introspekter(identityProvider: String, token: String): TexasIntrospeksjon {
		val beskrivelse = "identity_provider=$identityProvider"
		val respons = try {
			restClient.post()
				.uri(introspectionEndpoint)
				.contentType(MediaType.APPLICATION_JSON)
				.body(mapOf("identity_provider" to identityProvider, "token" to token))
				.retrieve()
				.body(Map::class.java)
		} catch (e: RestClientResponseException) {
			throw TexasUnavailableException("Texas svarte ${e.statusCode.value()} på introspeksjon for $beskrivelse")
		} catch (e: ResourceAccessException) {
			throw TexasUnavailableException("Fikk ikke kontakt med Texas for introspeksjon $beskrivelse (${e.javaClass.simpleName})")
		} catch (e: RestClientException) {
			throw TexasUnavailableException("Ugyldig svar fra Texas på introspeksjon for $beskrivelse (${e.javaClass.simpleName})")
		} ?: throw TexasUnavailableException("Tomt svar fra Texas på introspeksjon for $beskrivelse")

		val claims = respons.entries.associate { (navn, verdi) -> navn.toString() to verdi }
		return when (claims["active"]) {
			true -> TexasIntrospeksjon(aktiv = true, claims = claims - setOf("active", "error"))
			false -> TexasIntrospeksjon(aktiv = false, claims = emptyMap())
			else -> throw TexasUnavailableException("Svaret fra Texas manglet active for $beskrivelse")
		}
	}

	companion object {
		const val TOKENX = "tokenx"
		const val ENTRA_ID = "entra_id"
	}
}

/** Claimene skal aldri logges, så [toString] viser bare om tokenet er gyldig. */
class TexasIntrospeksjon(val aktiv: Boolean, val claims: Map<String, Any?>) {
	override fun toString(): String = "TexasIntrospeksjon(aktiv=$aktiv)"
}

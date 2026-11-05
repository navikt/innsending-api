package no.nav.soknad.innsending.consumerapis.token

/**
 * Henter token til kall mot andre tjenester (#461). Implementert av Texas-sidecaren i dev og prod.
 *
 * Feil blir [TokenServiceUnavailableException] uten respons-body og uten cause, slik at token aldri havner i logger.
 */
interface AccessTokenProvider {
	/** Maskintoken (client credentials) for [target], for eksempel et Entra ID-scope. */
	fun getMachineToken(target: String): String

	/** Bytter innbyggerens token (token exchange) til et token for [target], for eksempel en TokenX-audience. */
	fun exchangeUserToken(target: String, userToken: String): String
}

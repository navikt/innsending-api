package no.nav.soknad.innsending.consumerapis.azure.dto

import java.time.OffsetDateTime
import java.time.ZoneOffset

data class AzureAdV2TokenResponse(
	val access_token: String,
	val expires_in: Long,
	val token_type: String
)

fun AzureAdV2TokenResponse.toAzureAdV2Token(): AzureAdV2Token {
	val expiresOn = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(this.expires_in)
	return AzureAdV2Token(
		accessToken = this.access_token,
		expires = expiresOn
	)
}

package no.nav.soknad.innsending.config

data class AzureADV2TokenResponse(
  val access_token: String,
  val expires_in: Long,
  val token_type: String
)

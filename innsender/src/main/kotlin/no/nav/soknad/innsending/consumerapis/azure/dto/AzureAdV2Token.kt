package no.nav.soknad.innsending.consumerapis.azure.dto

import java.time.OffsetDateTime

data class AzureAdV2Token(
	val accessToken: String,
	val expires: OffsetDateTime
)


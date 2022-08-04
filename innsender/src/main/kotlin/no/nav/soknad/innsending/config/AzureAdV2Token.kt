package no.nav.soknad.innsending.config

import java.time.OffsetDateTime

data class AzureAdV2Token(
	val accessToken: String,
	val expires: OffsetDateTime
)


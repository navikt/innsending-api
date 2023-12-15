package no.nav.soknad.innsending.util

object Constants {
	const val SELVBETJENING = "selvbetjening"
	const val CLAIM_ACR_IDPORTEN_LOA_HIGH = "acr=idporten-loa-high"
	const val TOKENX = "tokenx"
	const val AZURE = "azure"

	const val NAV_CONSUMER_ID = "Nav-Consumer-Id"
	const val HEADER_CALL_ID = "Nav-Call-Id"
	const val CORRELATION_ID = "correlation_id"
	const val MDC_INNSENDINGS_ID = "innsendingsId"
	const val NAV_PERSON_IDENT = "NAV-Personident"

	const val BEARER = "Bearer "

	const val MAX_AKTIVE_DAGER = 3 * 365L

	const val DEFAULT_LEVETID_OPPRETTET_SOKNAD = 56L // 8 uker inntil ikke innsendt søknad/ettersendingssøknad slettes
	const val DEFAULT_FRIST_FOR_ETTERSENDELSE = 14L // 2 uker på å ettersende manglende vedlegg (NB myk frist)

	const val KVITTERINGS_NR = "L7"

	const val ukjentEttersendingsId = "-1" // sette lik innsendingsid istedenfor?

	// External services for prefill data
	const val PDL = "pdl"
	const val ARENA_MAALGRUPPER = "arena_maalgrupper"
	const val ARENA_AKTIVITETER = "arena_aktiviteter"

}

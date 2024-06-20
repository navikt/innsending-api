package no.nav.soknad.innsending.util

object Constants {
	const val SELVBETJENING = "selvbetjening"
	const val CLAIM_ACR_IDPORTEN_LOA_HIGH = "acr=idporten-loa-high"
	const val CLAIM_ACR_LEVEL_4 = "acr=Level4"
	const val TOKENX = "tokenx"
	const val AZURE = "azuread"

	const val NAV_CONSUMER_ID = "Nav-Consumer-Id"
	const val HEADER_CALL_ID = "Nav-Call-Id"
	const val HEADER_INNSENDINGSID = "x-innsendingsId"
	const val CORRELATION_ID = "correlation_id"
	const val MDC_INNSENDINGS_ID = "innsendingsId"
	const val NAV_PERSON_IDENT = "NAV-Personident"
	const val HEADER_BEHANDLINGSNUMMER = "behandlingsnummer"

	const val BEARER = "Bearer "
	const val AUTHORIZATION = "Authorization"

	const val MAX_AKTIVE_DAGER = 3 * 365L

	const val DEFAULT_LEVETID_OPPRETTET_SOKNAD = 56L // 8 uker inntil ikke innsendt søknad/ettersendingssøknad slettes
	const val DEFAULT_FRIST_FOR_ETTERSENDELSE = 14L // 2 uker på å ettersende manglende vedlegg (NB myk frist)

	const val KVITTERINGS_NR = "L7"

	const val ukjentEttersendingsId = "-1" // sette lik innsendingsid istedenfor?

	const val PDL_BEHANDLINGSNUMMER = "B613"

	// External services for prefill data
	const val PDL = "pdl"
	const val ARENA_MAALGRUPPE = "arena_maalgruppe"
	const val KONTORREGISTER_BORGER = "kontorregister_borger"

	const val LOSPOST_SKJEMANUMMER = "NAV 00-03.00"
}

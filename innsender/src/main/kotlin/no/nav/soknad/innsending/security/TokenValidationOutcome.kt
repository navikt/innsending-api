package no.nav.soknad.innsending.security

import java.util.concurrent.ConcurrentHashMap

/**
 * Utfallet av tokenvalideringen for et endepunkt som krever token (#461). Brukes i metrikken
 * `innsendingapi_token_validation_total` og i loggen ved 403.
 *
 * Verdiene er faste, så innringeren kan ikke bestemme hva som havner i logg og metrikk.
 */
enum class TokenValidationResult(val label: String) {
	/** Tokenet er gyldig, og endepunktet slipper forespørselen inn. */
	OK("ok"),

	/** Ingen bearer-token. */
	MISSING("missing"),

	/** Tokenet er ikke et JWT, eller headeren har flere token. */
	MALFORMED("malformed"),

	/** iss mangler eller er ingen av utstederne vi godtar. */
	UNKNOWN_ISSUER("unknown_issuer"),

	/** Texas sa at tokenet ikke er gyldig, for eksempel utløpt, feil audience eller feil signatur. */
	INACTIVE("inactive"),

	/** Tokenet er gyldig, men gir ikke tilgangen endepunktet krever. */
	FORBIDDEN("forbidden"),

	/** Texas svarte ikke, så vi vet ikke om tokenet er gyldig. Gir 503. */
	UNAVAILABLE("unavailable"),
}

/** Det [TokenAuthenticationFilter] fant ut. [issuer] er bare satt når iss tilhører en utsteder vi godtar. */
data class TokenCheck(val result: TokenValidationResult, val issuer: TokenIssuer?) {
	companion object {
		const val ATTRIBUTE = "no.nav.soknad.innsending.token-check"
	}
}

/**
 * Utfallet for forespørselen, satt av [AccessInterceptor]. [toString] brukes i loggen og inneholder bare faste verdier
 * og klient-appen fra et token Texas har verifisert.
 */
data class TokenValidationOutcome(val result: TokenValidationResult, val issuer: String, val client: String) {
	override fun toString(): String = "result=${result.label}, issuer=$issuer, client=$client"

	companion object {
		const val ATTRIBUTE = "no.nav.soknad.innsending.token-validation-outcome"
		const val UNKNOWN = "unknown"

		fun issuerLabel(issuer: TokenIssuer?): String = issuer?.name?.lowercase() ?: UNKNOWN
	}
}

/** Teller utfallet. Implementert av InnsenderMetrics. */
fun interface TokenValidationRecorder {
	fun record(outcome: TokenValidationOutcome)
}

/**
 * Lager `client`-verdien til logg og metrikk.
 *
 * Verdien skal bare komme fra et token Texas har verifisert. Entra og TokenX setter den, og bare apper med tilgang i
 * inbound-policyen får token til oss. I tillegg avviser vi uventede tegn og lange verdier, og etter [maxClients] ulike
 * verdier blir resten [OTHER]. Antall tidsserier har dermed en øvre grense selv om konfigurasjonen skulle være feil.
 */
class ClientLabels(private val maxClients: Int = 50) {
	private val seen = ConcurrentHashMap.newKeySet<String>()

	fun of(token: ValidatedToken, issuer: TokenIssuer): String {
		val value = when (issuer) {
			TokenIssuer.AZURE -> token.stringClaim(AZP_NAME) ?: token.stringClaim(AZP)
			TokenIssuer.TOKENX -> token.stringClaim(CLIENT_ID) ?: token.stringClaim(AZP)
		}
		if (value == null || !allowed.matches(value)) return TokenValidationOutcome.UNKNOWN
		if (value in seen) return value
		// Sjekk og innlegging samlet, så taket holder også når flere tråder ser nye verdier samtidig
		synchronized(seen) {
			if (value in seen) return value
			if (seen.size >= maxClients) return OTHER
			seen.add(value)
			return value
		}
	}

	companion object {
		const val OTHER = "other"
		private const val AZP_NAME = "azp_name"
		private const val AZP = "azp"
		private const val CLIENT_ID = "client_id"
		private val allowed = Regex("^[A-Za-z0-9:._-]{1,120}$")
	}
}

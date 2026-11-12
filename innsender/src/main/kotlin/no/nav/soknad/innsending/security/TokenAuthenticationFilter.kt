package no.nav.soknad.innsending.security

import com.nimbusds.jwt.JWTParser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.soknad.innsending.consumerapis.token.TokenIntrospector
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Validerer innkommende token hos Texas (#461).
 *
 * Gyldig token blir en [TokenAuthentication] med claimene fra Texas, og tilgangsreglene er de samme som før.
 * Ugyldig token gir ingen autentisering, og `@PreAuthorize` avviser med 403. Svarer ikke Texas, merkes forespørselen
 * med [TOKEN_VALIDATION_UNAVAILABLE], og [AccessInterceptor] gir 503 i stedet for 403 hvis endepunktet krever token.
 * Offentlige ruter påvirkes ikke.
 *
 * Utstederen velges ut fra iss i tokenet, slik at hver forespørsel gir ett kall til Texas. iss leses uten å verifisere
 * tokenet og brukes bare til å velge utsteder. Texas verifiserer signatur, issuer og audience før tokenet godtas.
 * Ukjent eller manglende iss gir ingen autentisering, og `@PreAuthorize` avviser med 403.
 *
 * Texas validerer ett token om gangen. En header med flere kommaseparerte token gir derfor ingen autentisering.
 * Token-support godtok det før migreringen.
 *
 * Filteret legger resultatet i request-attributtet [TokenCheck.ATTRIBUTE], slik at [AccessInterceptor] kan telle og
 * logge hvorfor en forespørsel ble avvist. Attributtet påvirker ikke om forespørselen slipper inn.
 */
class TokenAuthenticationFilter(
	private val introspector: TokenIntrospector,
	/** Issuer-URL per utsteder, fra TOKEN_X_ISSUER og AZURE_OPENID_CONFIG_ISSUER */
	issuerUrls: Map<TokenIssuer, String>,
) : OncePerRequestFilter() {

	private val issuerForUrl: Map<String, TokenIssuer> =
		issuerUrls.entries.associate { (issuer, issuerUrl) -> issuerUrl to issuer }

	init {
		val missing = TokenIssuer.entries - issuerUrls.keys
		require(missing.isEmpty()) { "Mangler issuer for $missing" }
	}

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
		val context = SecurityContextHolder.getContext()
		if (context.authentication == null) {
			// Resultatet brukes bare til logg og metrikk i AccessInterceptor, ikke til tilgangsbeslutningen
			request.setAttribute(TokenCheck.ATTRIBUTE, validate(bearerToken(request), request))
		}
		filterChain.doFilter(request, response)
	}

	private fun validate(token: String?, request: HttpServletRequest): TokenCheck {
		if (token == null) return TokenCheck(TokenValidationResult.MISSING, null)
		if (token.contains(',')) return TokenCheck(TokenValidationResult.MALFORMED, null)

		// Bare for å velge utsteder. Tokenet er ikke verifisert her
		val claims = runCatching { JWTParser.parse(token).jwtClaimsSet }.getOrNull()
			?: return TokenCheck(TokenValidationResult.MALFORMED, null)
		val issuer = claims.issuer?.let(issuerForUrl::get) ?: return TokenCheck(TokenValidationResult.UNKNOWN_ISSUER, null)

		return try {
			val result = introspector.introspect(issuer, token)
			if (result.active) {
				SecurityContextHolder.getContext().authentication = TokenAuthentication(issuer, result.claims, token)
				TokenCheck(TokenValidationResult.OK, issuer)
			} else {
				TokenCheck(TokenValidationResult.INACTIVE, issuer)
			}
		} catch (e: TokenServiceUnavailableException) {
			// Meldingen lager vi selv, uten token eller respons-body
			logger.warn("Kunne ikke validere token hos Texas: ${e.message}")
			request.setAttribute(TOKEN_VALIDATION_UNAVAILABLE, true)
			TokenCheck(TokenValidationResult.UNAVAILABLE, issuer)
		} catch (e: RuntimeException) {
			// Uventet feil i valideringen skal gi 503 som Texas-feil, ikke Spring sin feilside. Bare klassenavnet logges
			logger.warn("Uventet feil ved validering av token hos Texas (${e.javaClass.simpleName})")
			request.setAttribute(TOKEN_VALIDATION_UNAVAILABLE, true)
			TokenCheck(TokenValidationResult.UNAVAILABLE, issuer)
		}
	}

	companion object {
		/** Request-attributt som sier at Texas ikke svarte, slik at avvisningen blir 503 og ikke 403. */
		const val TOKEN_VALIDATION_UNAVAILABLE = "no.nav.soknad.innsending.token-validation-unavailable"

		private const val BEARER = "Bearer "

		fun bearerToken(request: HttpServletRequest): String? =
			request.getHeader(HttpHeaders.AUTHORIZATION)
				?.takeIf { it.length > BEARER.length && it.regionMatches(0, BEARER, 0, BEARER.length, ignoreCase = true) }
				?.substring(BEARER.length)
				?.trim()
				?.takeIf { it.isNotEmpty() }
	}
}

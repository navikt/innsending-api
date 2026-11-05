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
 * Validerer innkommende token hos Texas for utstederne Texas har overtatt (#461, steg 3i og 3j).
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
 * Texas validerer ett token om gangen. En header med flere kommaseparerte token gir derfor ingen autentisering fra
 * Texas. Token-support godtok det, men skyggemodus teller om det forekommer (utfallet flere_token).
 */
class TokenAuthenticationFilter(
	private val introspector: TokenIntrospector,
	issuers: Set<TokenIssuer>,
	/** Issuer-URL per utsteder, fra TOKEN_X_ISSUER og AZURE_OPENID_CONFIG_ISSUER */
	issuerUrls: Map<TokenIssuer, String>,
) : OncePerRequestFilter() {

	private val issuerForUrl: Map<String, TokenIssuer> =
		issuerUrls.filterKeys { it in issuers }.entries.associate { (issuer, issuerUrl) -> issuerUrl to issuer }

	init {
		val missing = issuers - issuerUrls.keys
		require(missing.isEmpty()) { "Mangler issuer for $missing" }
	}

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
		val context = SecurityContextHolder.getContext()
		val token = bearerToken(request)
		if (context.authentication == null && token != null && !token.contains(',')) {
			try {
				authenticate(token)?.let { context.authentication = it }
			} catch (e: TokenServiceUnavailableException) {
				// Meldingen lager vi selv, uten token eller respons-body
				logger.warn("Kunne ikke validere token hos Texas: ${e.message}")
				request.setAttribute(TOKEN_VALIDATION_UNAVAILABLE, true)
			}
		}
		filterChain.doFilter(request, response)
	}

	private fun authenticate(token: String): TokenAuthentication? {
		val issuer = unverifiedIssuer(token)?.let(issuerForUrl::get) ?: return null
		return introspector.introspect(issuer, token)
			.takeIf { it.active }
			?.let { TokenAuthentication(issuer, it.claims, token) }
	}

	// Bare for å velge utsteder. Tokenet er ikke verifisert her
	private fun unverifiedIssuer(token: String): String? =
		runCatching { JWTParser.parse(token).jwtClaimsSet.issuer }.getOrNull()


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

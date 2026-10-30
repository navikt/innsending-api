package no.nav.soknad.innsending.security

import com.nimbusds.jwt.JWTParser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjonKlient
import no.nav.soknad.innsending.consumerapis.texas.TexasUnavailableException
import no.nav.soknad.innsending.consumerapis.texas.TokenIntrospeksjon
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Validerer innkommende token hos Texas for utstederne Texas har overtatt (#461, steg 3i og 3j).
 *
 * Gyldig token blir en [TokenAutentisering] med claimene fra Texas, og tilgangsreglene er de samme som før.
 * Ugyldig token gir ingen autentisering, og `@PreAuthorize` avviser med 403. Svarer ikke Texas, merkes forespørselen
 * med [TEXAS_FEIL], og [TilgangInterceptor] gir 503 i stedet for 403 hvis endepunktet krever token.
 * Offentlige ruter påvirkes ikke.
 *
 * Utstederen velges ut fra iss i tokenet, slik at hver forespørsel gir ett kall til Texas. iss leses uten å verifisere
 * tokenet og brukes bare til å velge utsteder. Texas verifiserer signatur, issuer og audience før tokenet godtas.
 * Ukjent eller manglende iss gir ingen autentisering, og `@PreAuthorize` avviser med 403.
 *
 * Texas validerer ett token om gangen. En header med flere kommaseparerte token gir derfor ingen autentisering fra
 * Texas. Token-support godtok det, men skyggemodus teller om det forekommer (utfallet flere_token).
 */
class TexasAutentiseringFilter(
	private val introspeksjon: TokenIntrospeksjon,
	utstedere: Set<TokenUtsteder>,
	/** Issuer-URL per utsteder, fra TOKEN_X_ISSUER og AZURE_OPENID_CONFIG_ISSUER */
	issuere: Map<TokenUtsteder, String>,
) : OncePerRequestFilter() {

	private val utstederForIssuer: Map<String, TokenUtsteder> =
		issuere.filterKeys { it in utstedere }.entries.associate { (utsteder, issuer) -> issuer to utsteder }

	init {
		val mangler = utstedere - issuere.keys
		require(mangler.isEmpty()) { "Mangler issuer for $mangler" }
	}

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
		val context = SecurityContextHolder.getContext()
		val token = bearerToken(request)
		if (context.authentication == null && token != null && !token.contains(',')) {
			try {
				autentiser(token)?.let { context.authentication = it }
			} catch (e: TexasUnavailableException) {
				// Meldingen lager vi selv, uten token eller respons-body
				logger.warn("Kunne ikke validere token hos Texas: ${e.message}")
				request.setAttribute(TEXAS_FEIL, true)
			}
		}
		filterChain.doFilter(request, response)
	}

	private fun autentiser(token: String): TokenAutentisering? {
		val utsteder = uverifisertIssuer(token)?.let(utstederForIssuer::get) ?: return null
		return introspeksjon.introspekter(identityProvider(utsteder), token)
			.takeIf { it.aktiv }
			?.let { TokenAutentisering(utsteder, it.claims, token) }
	}

	// Bare for å velge utsteder. Tokenet er ikke verifisert her
	private fun uverifisertIssuer(token: String): String? =
		runCatching { JWTParser.parse(token).jwtClaimsSet.issuer }.getOrNull()

	private fun identityProvider(utsteder: TokenUtsteder) = when (utsteder) {
		TokenUtsteder.TOKENX -> TexasIntrospeksjonKlient.TOKENX
		TokenUtsteder.AZURE -> TexasIntrospeksjonKlient.ENTRA_ID
	}

	companion object {
		/** Request-attributt som sier at Texas ikke svarte, slik at avvisningen blir 503 og ikke 403. */
		const val TEXAS_FEIL = "no.nav.soknad.innsending.texas-feil"

		private const val BEARER = "Bearer "

		fun bearerToken(request: HttpServletRequest): String? =
			request.getHeader(HttpHeaders.AUTHORIZATION)
				?.takeIf { it.length > BEARER.length && it.regionMatches(0, BEARER, 0, BEARER.length, ignoreCase = true) }
				?.substring(BEARER.length)
				?.trim()
				?.takeIf { it.isNotEmpty() }
	}
}

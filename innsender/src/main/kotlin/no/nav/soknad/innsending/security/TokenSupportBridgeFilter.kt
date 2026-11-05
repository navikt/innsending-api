package no.nav.soknad.innsending.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.soknad.innsending.util.Constants.AZURE
import no.nav.soknad.innsending.util.Constants.TOKENX
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Bro fra token-support til Spring Security (#461, steg 3a). Fjernes i steg 4.
 *
 * Token-supports filter har allerede validert tokenet når dette filteret kjører, fordi det ligger først i
 * servlet-kjeden. Her gjøres de validerte tokenene om til en [TokenAuthentication], slik at `@PreAuthorize` og
 * [SubjectHandlerInterface] kan lese det fra Spring Security. Ugyldige token har token-support allerede forkastet,
 * og da settes ingen autentisering.
 *
 * Filteret registreres bare i Spring Security-kjeden og er derfor ikke en Spring-bønne.
 */
class TokenSupportBridgeFilter(
	private val contextHolder: TokenValidationContextHolder,
	/** Utstederne token-support fortsatt validerer. Texas tar over én og én i steg 3i og 3j */
	private val issuers: Set<TokenIssuer> = TokenIssuer.entries.toSet(),
) : OncePerRequestFilter() {

	override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
		val context = SecurityContextHolder.getContext()
		if (context.authentication == null) {
			authenticationFromTokenSupport()?.let { context.authentication = it }
		}
		filterChain.doFilter(request, response)
	}

	// Token-support godtar flere kommaseparerte token i samme header. Alle validerte token tas med, som før
	private fun authenticationFromTokenSupport(): TokenAuthentication? {
		val validated = contextHolder.getTokenValidationContext()
		val tokens = listOf(TOKENX to TokenIssuer.TOKENX, AZURE to TokenIssuer.AZURE)
			.filter { (_, issuer) -> issuer in issuers }
			.mapNotNull { (issuerId, issuer) ->
				validated.getJwtToken(issuerId)?.let { jwt -> issuer to ValidatedToken(jwt.jwtTokenClaims.allClaims, jwt.encodedToken) }
			}
			.toMap()
		return if (tokens.isEmpty()) null else TokenAuthentication(tokens)
	}
}

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
 * servlet-kjeden. Her gjøres de validerte tokenene om til en [TokenAutentisering], slik at `@PreAuthorize` og
 * [SubjectHandlerInterface] kan lese det fra Spring Security. Ugyldige token har token-support allerede forkastet,
 * og da settes ingen autentisering.
 *
 * Filteret registreres bare i Spring Security-kjeden og er derfor ikke en Spring-bønne.
 */
class TokenSupportBroFilter(private val contextHolder: TokenValidationContextHolder) : OncePerRequestFilter() {

	override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
		val context = SecurityContextHolder.getContext()
		if (context.authentication == null) {
			autentiseringFraTokenSupport()?.let { context.authentication = it }
		}
		filterChain.doFilter(request, response)
	}

	// Token-support godtar flere kommaseparerte token i samme header. Alle validerte token tas med, som før
	private fun autentiseringFraTokenSupport(): TokenAutentisering? {
		val validert = contextHolder.getTokenValidationContext()
		val tokens = listOf(TOKENX to TokenUtsteder.TOKENX, AZURE to TokenUtsteder.AZURE)
			.mapNotNull { (issuer, utsteder) ->
				validert.getJwtToken(issuer)?.let { jwt -> utsteder to ValidertToken(jwt.jwtTokenClaims.allClaims, jwt.encodedToken) }
			}
			.toMap()
		return if (tokens.isEmpty()) null else TokenAutentisering(tokens)
	}
}

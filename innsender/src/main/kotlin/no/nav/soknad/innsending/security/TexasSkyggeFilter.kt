package no.nav.soknad.innsending.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Sender bearer-tokenet til [TexasSkygge] sammen med token-supports avgjørelse (#461, steg 3h). Fjernes i steg 4.
 * Ligger etter [TokenSupportBroFilter] i Spring Security-kjeden og påvirker aldri forespørselen.
 */
class TexasSkyggeFilter(private val skygge: TexasSkygge) : OncePerRequestFilter() {

	override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
		bearerToken(request)?.let { token ->
			skygge.sammenlign(token, SecurityContextHolder.getContext().authentication as? TokenAutentisering)
		}
		filterChain.doFilter(request, response)
	}

	private fun bearerToken(request: HttpServletRequest): String? =
		request.getHeader(HttpHeaders.AUTHORIZATION)
			?.takeIf { it.length > BEARER.length && it.regionMatches(0, BEARER, 0, BEARER.length, ignoreCase = true) }
			?.substring(BEARER.length)
			?.trim()
			?.takeIf { it.isNotEmpty() }

	private companion object {
		const val BEARER = "Bearer "
	}
}

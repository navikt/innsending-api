package no.nav.soknad.innsending.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Sender bearer-tokenet til [TexasShadow] sammen med token-supports avgjørelse (#461, steg 3h). Fjernes i steg 4.
 * Ligger etter [TokenSupportBridgeFilter] i Spring Security-kjeden og påvirker aldri forespørselen.
 */
class TexasShadowFilter(private val shadow: TexasShadow) : OncePerRequestFilter() {

	override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
		bearerToken(request)?.let { token ->
			// Token-support godtar flere kommaseparerte token. Hele headeren skal ikke sendes til Texas som ett token
			if (token.contains(',')) shadow.multipleTokens()
			else shadow.compare(token, SecurityContextHolder.getContext().authentication as? TokenAuthentication)
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

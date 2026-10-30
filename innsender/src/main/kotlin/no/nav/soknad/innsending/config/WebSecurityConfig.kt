package no.nav.soknad.innsending.config

import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.soknad.innsending.security.TokenSupportBroFilter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.savedrequest.NullRequestCache

/**
 * Spring Security setter autentisering for forespørselen, mens tilgangsreglene ligger som `@PreAuthorize` på
 * controllerne (#461). Kjeden selv avviser ingen forespørsler, slik at offentlige ruter er uendret.
 *
 * Profilene med tokenvalidering har en [TokenValidationContextHolder] fra token-support. Da legges broen inn.
 * I local, docker og endtoend uten endtoend-auth finnes den ikke, og identiteten er fast (SubjectHandlerTestImpl).
 */
@Configuration
@EnableWebSecurity
class WebSecurityConfig {

	@Bean
	fun filterChain(http: HttpSecurity, tokenValidationContextHolder: ObjectProvider<TokenValidationContextHolder>): SecurityFilterChain {
		http.csrf { it.disable() }
		// Appen bruker bare bearer-token. Ingen sesjon og ingen lagret forespørsel å sende brukeren tilbake til
		http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
		http.requestCache { it.requestCache(NullRequestCache()) }
		tokenValidationContextHolder.ifAvailable { holder ->
			http.addFilterBefore(TokenSupportBroFilter(holder), AnonymousAuthenticationFilter::class.java)
		}
		return http.build()
	}
}

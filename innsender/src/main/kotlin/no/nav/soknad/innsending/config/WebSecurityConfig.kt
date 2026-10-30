package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.consumerapis.texas.TokenIntrospeksjon
import no.nav.soknad.innsending.security.TexasAutentiseringFilter
import no.nav.soknad.innsending.security.TokenUtsteder
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.savedrequest.NullRequestCache

/**
 * Texas validerer innkommende token, Spring Security setter autentiseringen for forespørselen, og tilgangsreglene
 * ligger som `@PreAuthorize` på controllerne (#461). Kjeden selv avviser ingen forespørsler, slik at offentlige ruter
 * er åpne.
 *
 * Profilene med tokenvalidering (test, dev, prod og endtoend-auth) har en [TokenIntrospeksjon]. I local, docker og
 * endtoend uten endtoend-auth finnes den ikke, og identiteten er fast (SubjectHandlerTestImpl).
 */
@Configuration
@EnableWebSecurity
class WebSecurityConfig {

	@Bean
	fun filterChain(
		http: HttpSecurity,
		tokenIntrospeksjon: ObjectProvider<TokenIntrospeksjon>,
		@Value("\${texas.innkommende.issuer.tokenx:}") tokenxIssuer: String,
		@Value("\${texas.innkommende.issuer.azure:}") azureIssuer: String,
	): SecurityFilterChain {
		http.csrf { it.disable() }
		// Appen bruker bare bearer-token. Ingen sesjon og ingen lagret forespørsel å sende brukeren tilbake til
		http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
		http.requestCache { it.requestCache(NullRequestCache()) }

		tokenIntrospeksjon.ifAvailable { introspeksjon ->
			val issuere = mapOf(TokenUtsteder.TOKENX to tokenxIssuer, TokenUtsteder.AZURE to azureIssuer)
				.filterValues { it.isNotBlank() }
			http.addFilterBefore(
				TexasAutentiseringFilter(introspeksjon, issuere),
				AnonymousAuthenticationFilter::class.java,
			)
		}
		return http.build()
	}
}

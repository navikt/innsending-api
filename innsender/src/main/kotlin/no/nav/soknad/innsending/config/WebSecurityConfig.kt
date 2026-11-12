package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.consumerapis.token.TokenIntrospector
import no.nav.soknad.innsending.security.TokenAuthenticationFilter
import no.nav.soknad.innsending.security.TokenIssuer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
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
 * Profilene med tokenvalidering (test, dev, prod og endtoend-auth) har en [TokenIntrospector]. I local, docker og
 * endtoend uten endtoend-auth finnes den ikke, og identiteten er fast (SubjectHandlerTestImpl). I dev og prod stopper
 * oppstarten hvis den mangler, slik at en feil i profilene ikke gir en app som avviser alle kall.
 */
@Configuration
@EnableWebSecurity
class WebSecurityConfig {

	@Bean
	fun filterChain(
		http: HttpSecurity,
		tokenIntrospector: ObjectProvider<TokenIntrospector>,
		environment: Environment,
		@Value("\${texas.incoming.issuer.tokenx:}") tokenxIssuer: String,
		@Value("\${texas.incoming.issuer.azure:}") azureIssuer: String,
	): SecurityFilterChain {
		http.csrf { it.disable() }
		// Appen bruker bare bearer-token. Ingen sesjon og ingen lagret forespørsel å sende brukeren tilbake til
		http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
		http.requestCache { it.requestCache(NullRequestCache()) }

		val introspector = tokenIntrospector.ifAvailable
		requireTokenValidationInNais(environment, introspector)
		if (introspector != null) {
			val issuerUrls = mapOf(TokenIssuer.TOKENX to tokenxIssuer, TokenIssuer.AZURE to azureIssuer)
				.filterValues { it.isNotBlank() }
			http.addFilterBefore(
				TokenAuthenticationFilter(introspector, issuerUrls),
				AnonymousAuthenticationFilter::class.java,
			)
		}
		return http.build()
	}

	companion object {
		private val NAIS = Profiles.of("dev | prod")

		fun requireTokenValidationInNais(environment: Environment, tokenIntrospector: TokenIntrospector?) {
			check(tokenIntrospector != null || !environment.acceptsProfiles(NAIS)) {
				"Mangler TokenIntrospector i dev eller prod. Innkommende token kan ikke valideres"
			}
		}
	}
}

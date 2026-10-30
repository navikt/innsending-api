package no.nav.soknad.innsending.config

import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.soknad.innsending.consumerapis.texas.TokenIntrospeksjon
import no.nav.soknad.innsending.security.TexasAutentiseringFilter
import no.nav.soknad.innsending.security.TexasSkygge
import no.nav.soknad.innsending.security.TexasSkyggeFilter
import no.nav.soknad.innsending.security.TokenSupportBroFilter
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
 * Spring Security setter autentisering for forespørselen, mens tilgangsreglene ligger som `@PreAuthorize` på
 * controllerne (#461). Kjeden selv avviser ingen forespørsler, slik at offentlige ruter er uendret.
 *
 * Hvem som validerer innkommende token, styres per utsteder av `texas.innkommende.utstedere` (#461, steg 3i og 3j).
 * Utstederne i lista valideres av Texas. De andre valideres fortsatt av token-support gjennom broen.
 * Profilene med tokenvalidering har en [TokenValidationContextHolder] fra token-support, og bare da legges broen inn.
 * I local, docker og endtoend uten endtoend-auth finnes den ikke, og identiteten er fast (SubjectHandlerTestImpl).
 */
@Configuration
@EnableWebSecurity
class WebSecurityConfig {

	@Bean
	fun filterChain(
		http: HttpSecurity,
		tokenValidationContextHolder: ObjectProvider<TokenValidationContextHolder>,
		tokenIntrospeksjon: ObjectProvider<TokenIntrospeksjon>,
		texasSkygge: ObjectProvider<TexasSkygge>,
		@Value("\${texas.innkommende.utstedere:}") texasUtstedere: String,
	): SecurityFilterChain {
		http.csrf { it.disable() }
		// Appen bruker bare bearer-token. Ingen sesjon og ingen lagret forespørsel å sende brukeren tilbake til
		http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
		http.requestCache { it.requestCache(NullRequestCache()) }

		val viaTexas = TokenUtsteder.fraListe(texasUtstedere)
		val viaTokenSupport = TokenUtsteder.entries.toSet() - viaTexas

		tokenValidationContextHolder.ifAvailable { holder ->
			http.addFilterBefore(TokenSupportBroFilter(holder, viaTokenSupport), AnonymousAuthenticationFilter::class.java)
		}
		if (viaTexas.isNotEmpty()) {
			val introspeksjon = tokenIntrospeksjon.ifAvailable
				?: error("texas.innkommende.utstedere=$viaTexas krever Texas, men profilen har ingen introspeksjon")
			http.addFilterBefore(TexasAutentiseringFilter(introspeksjon, viaTexas), AnonymousAuthenticationFilter::class.java)
		}
		// Skyggemodus (steg 3h) sammenligner bare utstederne token-support fortsatt validerer
		texasSkygge.ifAvailable { skygge ->
			http.addFilterBefore(TexasSkyggeFilter(skygge), AnonymousAuthenticationFilter::class.java)
		}
		return http.build()
	}
}

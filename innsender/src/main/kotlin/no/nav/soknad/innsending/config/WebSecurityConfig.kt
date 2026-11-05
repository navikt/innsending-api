package no.nav.soknad.innsending.config

import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.soknad.innsending.consumerapis.token.TokenIntrospector
import no.nav.soknad.innsending.security.TokenAuthenticationFilter
import no.nav.soknad.innsending.security.TexasShadow
import no.nav.soknad.innsending.security.TexasShadowFilter
import no.nav.soknad.innsending.security.TokenSupportBridgeFilter
import no.nav.soknad.innsending.security.TokenIssuer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter
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
		tokenIntrospector: ObjectProvider<TokenIntrospector>,
		texasShadow: ObjectProvider<TexasShadow>,
		@Value("\${texas.incoming.issuers:}") texasIssuers: String,
		@Value("\${texas.incoming.issuer.tokenx:}") tokenxIssuer: String,
		@Value("\${texas.incoming.issuer.azure:}") azureIssuer: String,
	): SecurityFilterChain {
		http.csrf { it.disable() }
		// Appen bruker bare bearer-token. Ingen sesjon og ingen lagret forespørsel å sende brukeren tilbake til
		http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
		http.requestCache { it.requestCache(NullRequestCache()) }

		val viaTexas = TokenIssuer.fromList(texasIssuers)
		val viaTokenSupport = TokenIssuer.entries.toSet() - viaTexas

		// Rekkefølgen er bro, Texas, skygge, og alle før AnonymousAuthenticationFilter: Texas validerer bare det broen
		// ikke har autentisert, og skyggemodus trenger avgjørelsene fra begge. Hvert filter forankres ved et eget
		// innebygd filter, slik at de får hvert sitt ordensnummer i stedet for å dele ett
		tokenValidationContextHolder.ifAvailable { holder ->
			http.addFilterBefore(TokenSupportBridgeFilter(holder, viaTokenSupport), SecurityContextHolderAwareRequestFilter::class.java)
		}
		if (viaTexas.isNotEmpty()) {
			val introspector = tokenIntrospector.ifAvailable
				?: error("texas.incoming.issuers=$viaTexas krever Texas, men profilen har ingen introspeksjon")
			val issuerUrls = mapOf(TokenIssuer.TOKENX to tokenxIssuer, TokenIssuer.AZURE to azureIssuer).filterValues { it.isNotBlank() }
			http.addFilterAfter(TokenAuthenticationFilter(introspector, viaTexas, issuerUrls), SecurityContextHolderAwareRequestFilter::class.java)
		}
		// Skyggemodus (steg 3h) sammenligner bare utstederne token-support fortsatt validerer
		texasShadow.ifAvailable { shadow -> http.addFilterBefore(TexasShadowFilter(shadow), AnonymousAuthenticationFilter::class.java) }
		return http.build()
	}
}

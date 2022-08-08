package no.nav.soknad.innsending.config

import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.core.Authentication


@EnableOAuth2Client(cacheEnabled = true)
@Configuration
@Profile("dev | prod")
class TokenClientConfig {

	@Bean
	fun noopAuthenticationManager(): AuthenticationManager? {
		return AuthenticationManager { authentication: Authentication? ->
			throw AuthenticationServiceException(
				"Bruker tokenx autentisering"
			)
		}
	}

}



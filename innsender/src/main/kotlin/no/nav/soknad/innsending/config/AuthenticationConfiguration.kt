package no.nav.soknad.innsending.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.core.Authentication


@Configuration
class AuthenticationConfiguration {
	@Bean
	fun noopAuthenticationManager(): AuthenticationManager? {
		return AuthenticationManager { authentication: Authentication? ->
			throw AuthenticationServiceException(
				"Bruker tokenx validering for autentisering"
			)
		}
	}
}

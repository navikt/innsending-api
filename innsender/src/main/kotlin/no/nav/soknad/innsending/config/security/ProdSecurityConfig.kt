package no.nav.soknad.innsending.config.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain


@Configuration
@Profile("!local")
class ProdSecurityConfig(
	private val securityFilterChainFactory: SecurityFilterChainFactory,
) {
	@Bean
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
		securityFilterChainFactory.authenticated(http)
}

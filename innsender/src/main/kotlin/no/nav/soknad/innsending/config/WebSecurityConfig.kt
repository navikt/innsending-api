package no.nav.soknad.innsending.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain


@Configuration
@EnableWebSecurity
class WebSecurityConfig {

	@Bean
	@Profile("test")
	fun filterChainTest(http: HttpSecurity): SecurityFilterChain {
		http.csrf { csrf ->
			csrf.disable()
		}
		return http.build()
	}
	@Bean
	@Profile("!test")
	fun filterChainProd(http: HttpSecurity): SecurityFilterChain {
		return http.build()
	}


}

package no.nav.soknad.innsending.config

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@EnableJwtTokenValidation(
	ignore = [
		"org.springframework",
		"no.nav.soknad.innsending.supervision",
		"io.swagger",
		"org.springdoc",
		"org.webjars.swagger-ui"
	]
)
@Profile("test | dev | prod")
@Configuration
class JwtTokenValidationConfig

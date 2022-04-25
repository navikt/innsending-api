package no.nav.soknad.innsending.config

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

//@EnableOAuth2Client(cacheEnabled = true)
@EnableJwtTokenValidation(ignore = [
	"org.springframework",
	"no.nav.soknad.innsending.rest.FrontEndRestApi",
	"no.nav.soknad.innsending.supervision",
	"io.swagger",
	"org.springdoc",
	"org.webjars.swagger-ui"
])
@Profile("dev | prod")
@Configuration
class JwtTokenValidationConfig {
}

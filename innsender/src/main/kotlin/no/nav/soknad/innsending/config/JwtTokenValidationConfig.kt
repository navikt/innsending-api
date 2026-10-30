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
// Med spring-security-tilgang (bare i test) validerer token-support fortsatt token, men controllerne håndheves
// bare av @PreAuthorize. Se AccessMatrixSpringSecurityTest (#461)
@Profile("(test | dev | prod) & !spring-security-tilgang")
@Configuration
class JwtTokenValidationConfig

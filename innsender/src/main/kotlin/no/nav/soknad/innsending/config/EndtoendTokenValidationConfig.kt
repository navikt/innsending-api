package no.nav.soknad.innsending.config

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Validerer innkommende token i e2e-testene i archiving-infrastructure, med samme regler som i dev og prod.
 *
 * Profilen endtoend-auth brukes sammen med endtoend. Stubbene for PDL, SAF og andre integrasjoner beholdes,
 * og utgående kall får ikke token. Bare innkommende validering og identitet fra token er ekte.
 */
@EnableJwtTokenValidation(
	ignore = [
		"org.springframework",
		"no.nav.soknad.innsending.supervision",
		"io.swagger",
		"org.springdoc",
		"org.webjars.swagger-ui"
	]
)
@Profile("endtoend-auth & !(test | dev | prod)")
@Configuration
class EndtoendTokenValidationConfig

package no.nav.soknad.innsending.security

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.test.context.ActiveProfiles

/**
 * Kjører hele tilgangsmatrisen med `@PreAuthorize` som eneste håndhever av tilgangsregler (#461).
 *
 * I [AccessMatrixTest] håndhever både token-support og Spring Security reglene, og token-support avviser først.
 * Her hopper token-support over controllerne, men validerer fortsatt token. Da må `@PreAuthorize` alene gi
 * samme svar for hvert endepunkt og hver tokentype. Det er den samme fordelingen som etter steg 3i og 3j,
 * bare med token-support i stedet for Texas som validerer.
 */
@ActiveProfiles("test", "spring-security-tilgang")
class AccessMatrixSpringSecurityTest : AccessMatrixTest()

@EnableJwtTokenValidation(
	ignore = [
		"org.springframework",
		"no.nav.soknad.innsending.supervision",
		"no.nav.soknad.innsending.rest",
		"io.swagger",
		"org.springdoc",
		"org.webjars.swagger-ui"
	]
)
@Profile("test & spring-security-tilgang")
@Configuration
class JwtTokenValidationUtenTilgangsreglerConfig

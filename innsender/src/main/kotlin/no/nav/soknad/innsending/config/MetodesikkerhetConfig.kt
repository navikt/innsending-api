package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.security.TilgangInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Slår på `@PreAuthorize` i profilene som validerer innkommende token (#461).
 *
 * [TilgangInterceptor] sjekker reglene før request-bodyen leses, og metodesikkerheten sjekker dem igjen når
 * controller-metoden kalles. I local, docker og endtoend uten endtoend-auth valideres ikke token, og identiteten er
 * fast. Der håndheves heller ikke tilgangsreglene, på samme måte som token-support ikke er aktiv der.
 */
@Configuration
@EnableMethodSecurity
@Profile("test | dev | prod | endtoend-auth")
class MetodesikkerhetConfig : WebMvcConfigurer {

	override fun addInterceptors(registry: InterceptorRegistry) {
		registry.addInterceptor(TilgangInterceptor())
	}
}

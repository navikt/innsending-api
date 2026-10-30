package no.nav.soknad.innsending.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

/**
 * Slår på `@PreAuthorize` i profilene som validerer innkommende token (#461).
 *
 * I local, docker og endtoend uten endtoend-auth valideres ikke token, og identiteten er fast. Der skal tilgangsreglene
 * heller ikke håndheves, på samme måte som token-support ikke er aktiv der.
 */
@Configuration
@EnableMethodSecurity
@Profile("test | dev | prod | endtoend-auth")
class MetodesikkerhetConfig

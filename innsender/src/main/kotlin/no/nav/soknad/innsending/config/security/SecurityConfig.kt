package no.nav.soknad.innsending.config.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
@EnableMethodSecurity
class SecurityConfig(
	@Value("\${auth.issuers.azuread.jwk-set-uri}") private val azureadJwkUri: String,
	@Value("\${auth.issuers.tokenx.jwk-set-uri}") private val tokenxJwkUri: String,
) {
	@Bean
	fun azureJwtDecoder(): JwtDecoder =
		NimbusJwtDecoder.withJwkSetUri(azureadJwkUri).build()

	@Bean
	fun tokenxJwtDecoder(): JwtDecoder =
		NimbusJwtDecoder.withJwkSetUri(tokenxJwkUri).build()
}

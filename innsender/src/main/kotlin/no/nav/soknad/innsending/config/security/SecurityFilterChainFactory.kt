package no.nav.soknad.innsending.config.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.Base64

@Component
class SecurityFilterChainFactory(
	@Value("\${auth.issuers.azuread.issuer-uri}") private val azureadIssuer: String,
	@Value("\${auth.issuers.tokenx.issuer-uri}") private val tokenxIssuer: String,
	@Qualifier("azureJwtDecoder") private val azureJwtDecoder: JwtDecoder,
	@Qualifier("tokenxJwtDecoder") private val tokenxJwtDecoder: JwtDecoder,
	private val localDevelopmentAuthenticationFilter: LocalDevelopmentAuthenticationFilter,
) {
	private val logger = LoggerFactory.getLogger(javaClass)
	private val mapper = jacksonObjectMapper()

	fun authenticated(http: HttpSecurity): SecurityFilterChain {
		http
			.csrf { csrf ->
				csrf.ignoringRequestMatchers(*PUBLIC_REQUEST_MATCHERS)
			}
			.authorizeHttpRequests { auth ->
				auth.requestMatchers(*PUBLIC_REQUEST_MATCHERS).permitAll()
				auth.anyRequest().authenticated()
			}
			.oauth2ResourceServer { rs ->
				rs.jwt { jwt ->
					jwt.decoder(delegatingJwtDecoder())
					jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
				}
			}

		return http.build()
	}

	fun localAuthDisabled(http: HttpSecurity): SecurityFilterChain {
		http
			.csrf { csrf ->
				csrf.disable()
			}
			.authorizeHttpRequests { auth ->
				auth.requestMatchers(*PUBLIC_REQUEST_MATCHERS).permitAll()
				auth.anyRequest().permitAll()
			}
			.addFilterBefore(localDevelopmentAuthenticationFilter, AnonymousAuthenticationFilter::class.java)

		return http.build()
	}

	private fun delegatingJwtDecoder(): JwtDecoder =
		JwtDecoder { token ->
			val issuer = extractIssuer(token) ?: run {
				logger.info("Missing issuer (iss) in token")
				throw BadCredentialsException("Missing issuer (iss) in token")
			}

			when (issuer) {
				azureadIssuer -> azureJwtDecoder.decode(token)
				tokenxIssuer -> tokenxJwtDecoder.decode(token)
				else -> {
					logger.info("Unknown issuer: $issuer")
					throw BadCredentialsException("Unknown issuer: $issuer")
				}
			}
		}

	private fun jwtAuthenticationConverter() =
		JwtAuthenticationConverter().apply {
			setJwtGrantedAuthoritiesConverter(JwtGrantedAuthoritiesConverter())
		}

	private fun extractIssuer(tokenValue: String): String? {
		val parts = tokenValue.split(".")
		if (parts.size < 2) return null

		val payloadPart = try {
			Base64.getUrlDecoder().decode(parts[1])
		} catch (_: IllegalArgumentException) {
			return null
		}

		val payload = try {
			String(payloadPart)
		} catch (_: Exception) {
			return null
		}

		val node = try {
			mapper.readTree(payload)
		} catch (_: Exception) {
			return null
		}

		return node.get("iss")?.asText()
	}

	private companion object {
		private val PUBLIC_REQUEST_MATCHERS = arrayOf(
			"/health/isAlive",
			"/health/isReady",
			"/health/ping",
			"/health/status",
			"/health/backends",
			"/health/health/**",
			"/internal/metrics",
			"/internal/prometheus",
			"/public/**",
			"/swagger-ui",
			"/swagger-ui.html",
			"/swagger-ui/**",
			"/v3/api-docs",
			"/v3/api-docs.yaml",
			"/v3/api-docs/**",
			)
	}
}


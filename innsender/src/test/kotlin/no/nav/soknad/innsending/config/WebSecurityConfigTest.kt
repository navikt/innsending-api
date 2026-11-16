package no.nav.soknad.innsending.config

import io.mockk.mockk
import no.nav.soknad.innsending.consumerapis.token.TokenIntrospector
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertFailsWith

class WebSecurityConfigTest {

	@ParameterizedTest
	@ValueSource(strings = ["dev", "prod"])
	fun `startup fails in dev and prod without token validation`(profile: String) {
		assertFailsWith<IllegalStateException> {
			WebSecurityConfig.requireTokenValidationInNais(environment(profile), null)
		}
	}

	@ParameterizedTest
	@ValueSource(strings = ["dev", "prod"])
	fun `startup continues in dev and prod with token validation`(profile: String) {
		assertDoesNotThrow { WebSecurityConfig.requireTokenValidationInNais(environment(profile), mockk<TokenIntrospector>()) }
	}

	@ParameterizedTest
	@ValueSource(strings = ["local", "docker", "endtoend"])
	fun `profiles without token validation start without an introspector`(profile: String) {
		assertDoesNotThrow { WebSecurityConfig.requireTokenValidationInNais(environment(profile), null) }
	}

	@Test
	fun `no active profile starts without an introspector`() {
		assertDoesNotThrow { WebSecurityConfig.requireTokenValidationInNais(MockEnvironment(), null) }
	}

	private fun environment(profile: String) = MockEnvironment().apply { setActiveProfiles(profile) }
}

package no.nav.soknad.innsending.utils

import no.nav.security.mock.oauth2.MockOAuth2Server
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * Én MockOAuth2Server for hele testkjøringen. Utsteder tokenene testene sender, og FakeTexasIntrospeksjon validerer
 * dem mot nøklene her. Erstatter @EnableMockOAuth2Server fra token-support (#461).
 */
object TestMockOAuth2Server {
	val server: MockOAuth2Server by lazy { MockOAuth2Server().apply { start() } }

	val port: Int
		get() = server.baseUrl().port
}

@TestConfiguration
class TestMockOAuth2ServerConfig {
	@Bean
	fun mockOAuth2Server(): MockOAuth2Server = TestMockOAuth2Server.server
}

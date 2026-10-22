package no.nav.soknad.innsending.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.soknad.innsending.safselvbetjening.generated.HentDokumentOversikt
import net.logstash.logback.encoder.LogstashEncoder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SafSelvbetjeningClientConfigTest {

	private val server = WireMockServer(wireMockConfig().dynamicPort())
	private val logger = LoggerFactory.getLogger(SafSelvbetjeningClientConfig::class.java) as Logger
	private val responseLogs = CopyOnWriteArrayList<ILoggingEvent>()
	private val logged = CountDownLatch(1)
	private val appender = object : AppenderBase<ILoggingEvent>() {
		override fun append(event: ILoggingEvent) {
			if (event.message.startsWith("SAF response body")) {
				responseLogs.add(event)
				logged.countDown()
			}
		}
	}

	@BeforeEach
	fun setup() {
		server.start()
		appender.start()
		logger.addAppender(appender)
	}

	@AfterEach
	fun tearDown() {
		logger.detachAppender(appender)
		appender.stop()
		server.stop()
	}

	@Test
	fun `reads an archive response larger than the default 256 KiB limit`() {
		val body = archiveResponse(3000)
		assertTrue(body.toByteArray(Charsets.UTF_8).size > 256 * 1024)
		server.stubFor(post(urlEqualTo("/graphql")).willReturn(okJson(body)))

		val response = execute()

		assertEquals(3000, response.data?.dokumentoversiktSelvbetjening?.journalposter?.size)
		assertResponseSizeLogged(body)
		server.verify(1, postRequestedFor(urlEqualTo("/graphql")))
	}

	@Test
	fun `counts UTF-8 response bytes without relying on Content-Length`() {
		val body = archiveResponse(10)
		assertTrue(body.toByteArray(Charsets.UTF_8).size > body.length)
		server.stubFor(
			post(urlEqualTo("/graphql"))
				.willReturn(okJson(body).withChunkedDribbleDelay(4, 40))
		)

		val response = execute()

		assertEquals(10, response.data?.dokumentoversiktSelvbetjening?.journalposter?.size)
		assertResponseSizeLogged(body)
	}

	@Test
	fun `keeps the configured buffer limit and marks an interrupted body as incomplete`() {
		val body = archiveResponse(3000)
		server.stubFor(post(urlEqualTo("/graphql")).willReturn(okJson(body).withChunkedDribbleDelay(16, 160)))
		val config = RestConfig().apply { safMaxInMemorySize = DataSize.ofKilobytes(64) }

		val exception = assertThrows(WebClientResponseException::class.java) { execute(config) }

		assertTrue(generateSequence<Throwable>(exception) { it.cause }.any { it is DataBufferLimitException })
		assertTrue(logged.await(5, TimeUnit.SECONDS), "Expected a partial response-size log")
		assertEquals(1, responseLogs.size)
		val fields = responseLogs.first().keyValuePairs.associate { it.key to it.value }
		val receivedBytes = fields["saf_response_bytes_received"] as Long
		assertTrue(receivedBytes > config.safMaxInMemorySize.toBytes())
		assertTrue(receivedBytes < body.toByteArray(Charsets.UTF_8).size)
		assertEquals("cancel", fields["saf_response_body_terminal_signal"])
	}

	@Test
	fun `logs the size of an HTTP error response without logging its contents`() {
		val body = """{"error":"synthetic upstream failure"}"""
		server.stubFor(post(urlEqualTo("/graphql")).willReturn(aResponse().withStatus(503).withBody(body)))

		val exception = assertThrows(WebClientResponseException::class.java) { execute() }

		assertEquals(503, exception.statusCode.value())
		assertResponseSizeLogged(body)
	}

	@ParameterizedTest
	@ValueSource(longs = [-1, 0, 2147483648])
	fun `rejects unlimited or overflowing buffer sizes`(bytes: Long) {
		val config = RestConfig().apply { safMaxInMemorySize = DataSize.ofBytes(bytes) }

		assertThrows(IllegalArgumentException::class.java) { createClientConfig(config).graphQLClient() }
	}

	@Test
	fun `binds the SAF size override through application yaml`() {
		val environment = StandardEnvironment().apply {
			propertySources.addFirst(MapPropertySource("test", mapOf("SAF_MAX_IN_MEMORY_SIZE" to "2MB")))
			propertySources.addLast(YamlPropertySourceLoader().load("application", ClassPathResource("application.yml")).first())
		}

		val config = Binder.get(environment).bind("restconfig", RestConfig::class.java).get()

		assertEquals(DataSize.ofMegabytes(2), config.safMaxInMemorySize)
	}

	private fun assertResponseSizeLogged(body: String) {
		assertTrue(logged.await(5, TimeUnit.SECONDS), "Expected a response-size log")
		assertEquals(1, responseLogs.size)
		assertEquals("SAF response body received", responseLogs.single().formattedMessage)
		val fields = responseLogs.single().keyValuePairs.associate { it.key to it.value }
		assertEquals(body.toByteArray(Charsets.UTF_8).size.toLong(), fields["saf_response_bytes_received"])
		assertEquals("onComplete", fields["saf_response_body_terminal_signal"])
		val encoder = LogstashEncoder().apply {
			context = logger.loggerContext
			start()
		}
		try {
			val json = String(encoder.encode(responseLogs.single()), Charsets.UTF_8)
			val event = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(json)
			assertEquals(body.toByteArray(Charsets.UTF_8).size.toLong(), event["saf_response_bytes_received"].asLong())
			assertEquals("onComplete", event["saf_response_body_terminal_signal"].asText())
			assertFalse(json.contains("synthetic-identity"))
			assertFalse(json.contains("synthetic-token"))
			assertFalse(json.contains("Syntetisk"))
			assertFalse(json.contains("synthetic upstream failure"))
		} finally {
			encoder.stop()
		}
	}

	private fun execute(restConfig: RestConfig = RestConfig()) = runBlocking {
		createClientConfig(restConfig).graphQLClient().execute(HentDokumentOversikt(HentDokumentOversikt.Variables("synthetic-identity")))
	}

	private fun createClientConfig(restConfig: RestConfig): SafSelvbetjeningClientConfig {
		restConfig.safselvbetjeningUrl = server.baseUrl()
		val oauth2Config = ClientConfigurationProperties(mapOf("tokenx-safselvbetjening" to mockk<ClientProperties>()))
		val accessTokenService = mockk<OAuth2AccessTokenService>()
		every { accessTokenService.getAccessToken(any()) } returns OAuth2AccessTokenResponse(access_token = "synthetic-token")
		return SafSelvbetjeningClientConfig(restConfig, oauth2Config, accessTokenService, "innsending-api")
	}

	private fun archiveResponse(count: Int): String {
		val posts = (1..count).joinToString(",") {
			"""{"journalpostId":"$it","journalposttype":"I","relevanteDatoer":[],"tittel":"Syntetisk æøå ${"arkivmetadata ".repeat(8)}"}"""
		}
		return """{"data":{"dokumentoversiktSelvbetjening":{"journalposter":[$posts]}}}"""
	}
}

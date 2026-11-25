package no.nav.soknad.innsending

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.soknad.InnsendingApiApplication
import no.nav.soknad.innsending.utils.TestMockOAuth2Server
import no.nav.soknad.innsending.utils.TestMockOAuth2ServerConfig
import no.nav.soknad.innsending.utils.TestWireMockServer
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration


@ActiveProfiles("test")
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = ["spring.main.allow-bean-definition-overriding=true"],
	classes = [InnsendingApiApplication::class]
)
@ExtendWith(
	SpringExtension::class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(TestWebTestClientConfig::class, TestMockOAuth2ServerConfig::class)
class ApplicationTest() {

	@Autowired
	lateinit var webTestClient: WebTestClient

	val restTestClient: no.nav.soknad.innsending.utils.RestTestClient
		get() = no.nav.soknad.innsending.utils.RestTestClient(webTestClient)

	companion object {

		init {
			// Check at once and then every 10 ms, so bounded waits end shortly after the condition holds.
			Awaitility.setDefaultPollDelay(Duration.ZERO)
			Awaitility.setDefaultPollInterval(Duration.ofMillis(10))
		}

		@JvmField
		val wireMock: WireMockServer = TestWireMockServer.server

		@JvmStatic
		@DynamicPropertySource
		fun properties(reg: DynamicPropertyRegistry) {
			reg.add("wiremock.server.port") { wireMock.port().toString() }
			reg.add("mock-oauth2-server.port") { TestMockOAuth2Server.port.toString() }
			reg.add("antivirus.path") { "/antivirus/scan" }
			reg.add("arena.path") { "/arena-api/api/v1/tilleggsstoenad/dagligreise.*" }
			reg.add("arena-aktiviteter.path") { "/arena-api/api/v1/tilleggsstoenad/aktiviteter.*" }
			reg.add("arena-maalgrupper.path") { "/arena-api/api/v1/maalgrupper.*" }
			reg.add("azure-token.path") { "/azure-api" }
			reg.add("kodeverk-navskjema.path") { "/kodeverk-api/api/v1/kodeverk/NAVSkjema/koder.*" }
			reg.add("kodeverk-tema.path") { "/kodeverk-api/api/v1/kodeverk/Tema/koder.*" }
			reg.add("kodeverk-koder.path") { "/kodeverk-api/api/v1/kodeverk/Vedleggskoder/koder.*" }
			reg.add("kontoregister-borger.path") { "/kontoregister-api/api/borger/v1/hent-aktiv-konto.*" }
			reg.add("pdl-identer.path") { "/pdl-api/graphql" }
			reg.add("pdl-person.path") { "/pdl-api/graphql" }
			reg.add("pdl-prefill.path") { "/pdl-api/graphql" }
			reg.add("saf.path") { "/saf-api/graphql" }
			reg.add("token-exchange.path") { "/default/token" }
		}
	}

	@MockitoBean
	lateinit var prometheusRegistry: PrometheusRegistry

	@BeforeEach
	fun prepareWireMock() {
		// The static WireMock DSL stores its target per thread, so point it at the shared server before each test.
		WireMock.configureFor(WireMock(wireMock))
		// Calls that arrive between tests, such as late asynchronous calls, must not count in this test's verifications.
		wireMock.resetRequests()
	}

	@AfterEach
	fun resetWireMock() {
		// Removes stubs added by the test, the request journal and scenario states, and reloads the stubs from files.
		wireMock.resetToDefaultMappings()
	}

}

package no.nav.soknad.innsending

import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.soknad.InnsendingApiApplication
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
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
import org.springframework.transaction.annotation.EnableTransactionManagement


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
@Import(TestWebTestClientConfig::class)
@EnableTransactionManagement
@EnableMockOAuth2Server
class ApplicationTest() {

	@Autowired
	lateinit var webTestClient: WebTestClient

	val restTestClient: no.nav.soknad.innsending.utils.RestTestClient
		get() = no.nav.soknad.innsending.utils.RestTestClient(webTestClient)

	companion object {

		@JvmField
		@RegisterExtension
		val wireMock: WireMockExtension = WireMockExtension.newInstance()
			.configureStaticDsl(true)
			.options(
				wireMockConfig()
					//.port(5490)
					.dynamicPort()
					.notifier(ConsoleNotifier(true))
					.withRootDirectory("src/test/resources")
					.asynchronousResponseEnabled(false)
			)
			.build()

		@JvmStatic
		@DynamicPropertySource
		fun properties(reg: DynamicPropertyRegistry) {
			reg.add("wiremock.server.port") { wireMock.port.toString() }
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
			reg.add("sanity.path") { "/soknader/api/sanity/skjemautlisting" }
			reg.add("token-exchange.path") { "/default/token" }
		}
	}

	@MockitoBean
	lateinit var prometheusRegistry: PrometheusRegistry

}

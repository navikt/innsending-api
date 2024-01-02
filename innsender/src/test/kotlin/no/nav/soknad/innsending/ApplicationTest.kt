package no.nav.soknad.innsending

import io.prometheus.client.CollectorRegistry
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement


@ActiveProfiles("test")
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
	properties = ["spring.main.allow-bean-definition-overriding=true"],
	classes = [InnsendingApiApplication::class]
)
@EnableTransactionManagement
@EnableMockOAuth2Server(port = 1888)
@AutoConfigureWireMock(port = 5490)
class ApplicationTest {
	@MockBean
	lateinit var collectorRegistry: CollectorRegistry

}

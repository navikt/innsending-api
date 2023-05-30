package no.nav.soknad.innsending

import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@EnableTransactionManagement
@AutoConfigureWireMock(port = 9073)
@EnableMockOAuth2Server(port = 9898)
@ExtendWith(SpringExtension::class)
class ApplicationTest

package no.nav.soknad.innsending.location

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.model.EnvQualifier
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UrlHandlerImplTest {

	private lateinit var urlHandlerProd: UrlHandler

	@BeforeEach
	fun setup() {
		val map: Map<String, String> = mapOf(
			"default" to "http://default",
			EnvQualifier.preprodAltIntern.value to "http://preprod-alt",
		)
		val restConfig = RestConfig()
		restConfig.sendinn = RestConfig.SendInnConfig()
		restConfig.sendinn.urls = map
		urlHandlerProd = UrlHandlerImpl(restConfig)
	}

	@Test
	fun `Should always return default url in production`() {
		assertEquals("http://default", urlHandlerProd.getSendInnUrl(EnvQualifier.preprodAltIntern))
		assertEquals("http://default", urlHandlerProd.getSendInnUrl(EnvQualifier.preprodAltAnsatt))
		assertEquals("http://default", urlHandlerProd.getSendInnUrl(null))
		assertEquals("http://default", urlHandlerProd.getSendInnUrl())
	}

}

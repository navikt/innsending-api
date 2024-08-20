package no.nav.soknad.innsending.location

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.model.EnvQualifier
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UrlHandlerDevImplTest {

	private lateinit var urlHandlerDev: UrlHandler

	@BeforeEach
	fun setup() {
		val map: Map<String, String> = mapOf(
			"default" to "http://default",
			EnvQualifier.preprodAltIntern.value to "http://preprod-alt",
		)
		val restConfig = RestConfig()
		restConfig.sendinn = RestConfig.SendInnConfig()
		restConfig.sendinn.urls = map
		urlHandlerDev = UrlHandlerDevImpl(restConfig)
	}

	@Test
	fun `Should return url belonging to preferred env`() {
		val url = urlHandlerDev.getSendInnUrl(EnvQualifier.preprodAltIntern)
		assertEquals("http://preprod-alt", url)
	}

	@Test
	fun `Should return default url when preferred env does not exist`() {
		val url = urlHandlerDev.getSendInnUrl(EnvQualifier.preprodAnsatt)
		assertEquals("http://default", url)
	}

	@Test
	fun `Should return default url when no preferred env`() {
		val url = urlHandlerDev.getSendInnUrl()
		assertEquals("http://default", url)
	}

}

package no.nav.soknad.innsending.utils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig

/**
 * Én WireMock-server for hele testkjøringen. Porten må være den samme så lenge JVM-en lever, fordi Spring gjenbruker
 * testkontekster på tvers av klasser, og konteksten leser porten bare én gang. ApplicationTest nullstiller serveren
 * etter hver test.
 */
object TestWireMockServer {
	val server: WireMockServer by lazy {
		WireMockServer(
			wireMockConfig()
				.dynamicPort()
				.notifier(ConsoleNotifier(false))
				.withRootDirectory("src/test/resources")
				.asynchronousResponseEnabled(false)
		).apply {
			start()
			Runtime.getRuntime().addShutdownHook(Thread { stop() })
		}
	}
}

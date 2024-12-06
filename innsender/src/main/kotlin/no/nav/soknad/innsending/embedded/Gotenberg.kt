package no.nav.soknad.innsending.embedded

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

@Profile("!(prod | dev | docker)")
@Configuration
class Gotenberg {

	final val gotenberg: Container = Container()

	init {
		gotenberg.start()
	}

	@Bean(destroyMethod = "stop")
	fun embeddedGotenberg(): Container = gotenberg

	class Container(private val gotenbergPort: Int = 3000) : GenericContainer<Container>(
		DockerImageName.parse("europe-north1-docker.pkg.dev/nais-management-233d/fyllut-sendinn/upload-convert-to-pdf:8.0.0")
	) {

		private val logger = LoggerFactory.getLogger(javaClass)

		init {
			withExposedPorts(gotenbergPort)
				.waitingFor(Wait.forLogMessage(".*server listening on port 3000.*\\s", 1))
		}

		fun getUrl(): String = "http://$host:${getMappedPort(gotenbergPort)}"

		override fun stop() {
			logger.info("Logs from gotenberg: [${this.logs}]")
			super.stop()
		}

	}

}



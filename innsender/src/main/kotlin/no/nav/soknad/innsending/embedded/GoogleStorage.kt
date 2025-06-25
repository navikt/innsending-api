package no.nav.soknad.innsending.embedded

import com.github.dockerjava.api.command.InspectContainerResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse


@Profile("local | test")
@Configuration
class GoogleStorage {

	final val gcsServer: Container = Container()

	init {
		gcsServer.start()
	}

	@Bean(destroyMethod = "stop")
	fun embeddedGcs(): Container = gcsServer

	class Container(private val gcsPort: Int = 4443) : GenericContainer<Container>(
		DockerImageName.parse("fsouza/fake-gcs-server")
	) {

		private val logger = LoggerFactory.getLogger(javaClass)

		init {
			withExposedPorts(gcsPort)
				.withCreateContainerCmdModifier {
					it.withEntrypoint(
						"/bin/fake-gcs-server",
						"-scheme",
						"http",
						"-data",
						"/data"
					)
				}
				.waitingFor(Wait.forLogMessage(".*server started at.*\\s", 1))
		}

		fun getUrl(): String = "http://$host:${getMappedPort(gcsPort)}"

		override fun stop() {
			logger.info("Logs from Google Storage Server: [${this.logs}]")
			super.stop()
		}

		override fun containerIsStarted(containerInfo: InspectContainerResponse?) {
			super.containerIsStarted(containerInfo)
			updateExternalUrl()
		}

		private fun updateExternalUrl() {
		    val url = getUrl()
		    val modifyExternalUrlRequestUri = "$url/_internal/config"
		    val updateExternalUrlJson = """{"externalUrl": "$url"}"""

		    val req = HttpRequest.newBuilder()
		        .uri(URI.create(modifyExternalUrlRequestUri))
		        .header("Content-Type", "application/json")
		        .PUT(HttpRequest.BodyPublishers.ofString(updateExternalUrlJson))
		        .build()

		    val response = try {
		        HttpClient.newBuilder().build()
		            .send(req, HttpResponse.BodyHandlers.discarding())
		    } catch (e: IOException) {
		        throw RuntimeException("Failed to update fake-gcs-server external URL", e)
		    } catch (e: InterruptedException) {
		        Thread.currentThread().interrupt()
		        throw RuntimeException("Thread interrupted while updating fake-gcs-server external URL", e)
		    }

		    if (response.statusCode() != 200) {
		        throw RuntimeException("Error updating fake-gcs-server with external url, response status code ${response.statusCode()} != 200")
		    }
		}

	}

}

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

	// The container is shared and stopped when the JVM exits, not when a Spring context closes.
	// In tests every context uses its own bucket, see gcp.buckets.fillagerBucketNavn in application-test.yml.
	@Bean(destroyMethod = "")
	fun embeddedGcs(): Container = shared

	companion object {
		// One fake-gcs container per JVM, shared by every Spring context.
		val shared: Container by lazy {
			Container().apply {
				start()
				Runtime.getRuntime().addShutdownHook(Thread { stop() })
			}
		}
	}

	class Container(private val gcsPort: Int = 4443) : GenericContainer<Container>(
		DockerImageName.parse("fsouza/fake-gcs-server:1")
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
				.waitingFor(Wait.forHttp("/storage/v1/b").forPort(gcsPort))
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

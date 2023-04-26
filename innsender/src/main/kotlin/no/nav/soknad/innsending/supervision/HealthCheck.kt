package no.nav.soknad.innsending.supervision

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.api.HealthApi
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.model.ApplicationStatus
import no.nav.soknad.innsending.model.DependencyStatus
import no.nav.soknad.innsending.repository.AliveRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpServerErrorException
import java.time.OffsetDateTime

@RestController
@RequestMapping(value = ["/health"])
class HealthCheck(
	@Qualifier("pdl") private val pdlAPI: HealthRequestInterface,
	@Qualifier("saf") private val safAPI: HealthRequestInterface,
	@Qualifier("fillager") private val fillagerAPI: HealthRequestInterface,
	@Qualifier("mottaker") private val mottakerAPI: HealthRequestInterface,
	@Qualifier("notifikasjon") private val notifikasjonAPI: HealthRequestInterface,
	private val aliveRepository: AliveRepository
) : HealthApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	@GetMapping("/isAlive")
	override fun isAlive(): ResponseEntity<String> = ResponseEntity.status(HttpStatus.OK).body("ok")

	@GetMapping("/isReady")
	override fun isReady(): ResponseEntity<String> = if (applicationIsReady()) {
		ResponseEntity.status(HttpStatus.OK).body("Ready for action")
	} else {
		ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("/isReady called - application is not ready")
	}

	@GetMapping("/ping")
	override fun ping() = ResponseEntity.status(HttpStatus.OK).body("pong")


	@GetMapping("/status")
	override fun getStatus(): ResponseEntity<ApplicationStatus> {
		val dependencyStatusList = mutableListOf<DependencyStatus>()
		val backends = getBackends()
		runBlocking {
			backends
				.forEach {
					if (GlobalScope.async { it.dependencyEndpoint.invoke() }.await() != it.expectedResponse)
						dependencyStatusList.add(DependencyStatus(it.dependencyName, "Error", it.severity))
					else
						dependencyStatusList.add(DependencyStatus(it.dependencyName, "Ok", it.severity))
				}
		}
		val status = ApplicationStatus("Innsending-api", dependencyStatusList, OffsetDateTime.now())

		return ResponseEntity.status(HttpStatus.OK).body(status)
	}


	private fun applicationIsReady(): Boolean {
		val dependencies = getBackends()
		throwExceptionIfDependenciesAreDown(dependencies)

		return true
	}

	private fun getBackends(): List<Dependency> {
		return listOf(
			Dependency({ fillagerAPI.isReady() }, "ok", "Soknadsfillager", "Critical - Send in of applications will fail"),
			Dependency({ mottakerAPI.isReady() }, "ok", "SoknadsMottaker", "Critical  - Send in of applications will fail"),
			Dependency(
				{ notifikasjonAPI.isReady() },
				"ok",
				"Soknadsmottaker",
				"Critical - User notifictaions will not be published or cancelled"
			),
			Dependency(
				{ pdlAPI.isReady() },
				"ok",
				"PDL",
				"Non-Critical - Might affect users that recently have changed user identity"
			),
			Dependency(
				{ safAPI.isReady() },
				"ok",
				"SAF",
				"Non-Critical - Might affect information supplied in new application for sending supplenmentary documentation"
			),
			Dependency(
				{ aliveRepository.findTestById(1L) },
				"ok",
				"Database",
				"Critical - The application will not be able to create, read, update or delete the users applications"
			)
		)
	}

	/**
	 * Will throw exception if any of the dependencies are not returning the expected value.
	 * If all is well, the function will silently exit.
	 */
	private fun throwExceptionIfDependenciesAreDown(applications: List<Dependency>) {
		runBlocking {
			applications
				.map { Triple(GlobalScope.async { it.dependencyEndpoint.invoke() }, it.expectedResponse, it.dependencyName) }
				.forEach { if (it.first.await() != it.second) throwException("${it.third} does not seem to be up") }
		}
	}

	private fun throwException(message: String? = null): String {
		logger.warn(message)
		val status = HttpStatus.INTERNAL_SERVER_ERROR
		throw HttpServerErrorException(status, message ?: status.name)
	}

	private data class Dependency(
		val dependencyEndpoint: () -> String,
		val expectedResponse: String,
		val dependencyName: String,
		val severity: String
	)

}

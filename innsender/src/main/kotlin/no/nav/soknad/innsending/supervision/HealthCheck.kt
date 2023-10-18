package no.nav.soknad.innsending.supervision

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.api.HealthApi
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.model.ApplicationStatus
import no.nav.soknad.innsending.model.ApplicationStatusType
import no.nav.soknad.innsending.repository.AliveRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpServerErrorException

@RestController
@RequestMapping(value = ["/health"])
class HealthCheck(
	@Qualifier("pdl") private val pdlAPI: HealthRequestInterface,
	@Qualifier("saf") private val safAPI: HealthRequestInterface,
	@Qualifier("mottaker") private val mottakerAPI: HealthRequestInterface,
	@Qualifier("notifikasjon") private val notifikasjonAPI: HealthRequestInterface,
	private val aliveRepository: AliveRepository,
	@Value("\${status_log_url}") private val statusLogUrl: String
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
	override fun ping(): ResponseEntity<String> = ResponseEntity.status(HttpStatus.OK).body("pong")


	@GetMapping("/status")
	override fun getStatus(): ResponseEntity<ApplicationStatus> {
		val dependencyStatusList = mutableListOf<ApplicationStatus>()
		val backends = getBackends()
		runBlocking {
			backends
				.forEach {
					if (GlobalScope.async { it.dependencyEndpoint.invoke() }.await() != it.expectedResponse)
						dependencyStatusList.add(
							ApplicationStatus(
								status = it.status,
								description = it.description,
								logLink = it.logUrl
							)
						)
					else
						dependencyStatusList.add(
							ApplicationStatus(
								status = ApplicationStatusType.OK,
								description = "OK",
								logLink = it.logUrl
							)
						)
				}
		}

		val status = resolveMostCriticalStatus(dependencyStatusList)

		return ResponseEntity.status(HttpStatus.OK).body(status)
	}

	private fun resolveMostCriticalStatus(dependencyStatusList: List<ApplicationStatus>): ApplicationStatus {
		val errorStatus = dependencyStatusList.find { it.status == ApplicationStatusType.DOWN }
		val issueStatus = dependencyStatusList.find { it.status == ApplicationStatusType.ISSUE }

		return errorStatus ?: issueStatus ?: ApplicationStatus(ApplicationStatusType.OK, "OK")
	}


	private fun applicationIsReady(): Boolean {
		val dependencies = getBackends()
		throwExceptionIfDependenciesAreDown(dependencies)

		return true
	}

	private fun getBackends(): List<Dependency> {
		return listOf(
			Dependency(
				{ mottakerAPI.isReady() },
				expectedResponse = "ok",
				dependencyName = "SoknadsMottaker",
				description = "Can't access soknadsmottaker. Sending in applications will fail",
				status = ApplicationStatusType.DOWN,
				logUrl = statusLogUrl
			),
			Dependency(
				{ notifikasjonAPI.isReady() },
				expectedResponse = "ok",
				dependencyName = "Soknadsmottaker",
				description = "Can't send notifications. User notifictaions will not be published or cancelled",
				status = ApplicationStatusType.DOWN,
				logUrl = statusLogUrl
			),
			Dependency(
				{ pdlAPI.isReady() },
				expectedResponse = "ok",
				dependencyName = "PDL",
				description = "Can't connect to PDL. Might affect users that recently have changed user identity",
				status = ApplicationStatusType.ISSUE,
				logUrl = statusLogUrl
			),
			Dependency(
				{ safAPI.isReady() },
				expectedResponse = "ok",
				dependencyName = "SAF",
				"Can't connect to SAF. Might affect information supplied in new application for sending supplenmentary documentation",
				status = ApplicationStatusType.ISSUE,
				logUrl = statusLogUrl
			),
			Dependency(
				{ aliveRepository.findTestById(1L) },
				"ok",
				"Database",
				"Can't connect to database. The application will not be able to create, read, update or delete the users applications",
				status = ApplicationStatusType.DOWN,
				logUrl = statusLogUrl
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
		val description: String,
		val status: ApplicationStatusType,
		val logUrl: String
	)

}

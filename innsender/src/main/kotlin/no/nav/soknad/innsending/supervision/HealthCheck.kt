package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.api.HealthApi
import no.nav.soknad.innsending.model.ApplicationStatus
import no.nav.soknad.innsending.model.ApplicationStatusType
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(value = ["/health"])
class HealthCheck(@Value("\${status_log_url}") private val statusLogUrl: String) : HealthApi {

	@GetMapping("/isAlive")
	override fun isAlive(): ResponseEntity<String> = ResponseEntity.status(HttpStatus.OK).body("ok")

	@GetMapping("/isReady")
	override fun isReady(): ResponseEntity<String> = ResponseEntity.status(HttpStatus.OK).body("ok")

	@GetMapping("/ping")
	override fun ping(): ResponseEntity<String> = ResponseEntity.status(HttpStatus.OK).body("pong")

	@GetMapping("/status")
	override fun getStatus(): ResponseEntity<ApplicationStatus> {
		return ResponseEntity(
			ApplicationStatus(status = ApplicationStatusType.OK, description = "OK", logLink = statusLogUrl),
			HttpStatus.OK
		)
	}
}

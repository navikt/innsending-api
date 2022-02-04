package no.nav.soknad.innsending.supervision

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(value = ["/internal"])
class HealthCheck {

	@GetMapping("/isAlive")
	fun isAlive() = "ok"

	@GetMapping("/isReady")
	fun isReady() = "ok"

	@GetMapping("/ping")
	fun ping() = "pong"
}

package no.nav.soknad.innsending.exceptions

import nl.altindag.log.LogCaptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.test.assertTrue

// Avvisning fra @PreAuthorize skal gi samme svar som token-support (#461, steg 3g)
class TilgangsavvisningTest {

	private lateinit var mockMvc: MockMvc
	private lateinit var logCaptor: LogCaptor

	@BeforeEach
	fun setUp() {
		mockMvc = MockMvcBuilders
			.standaloneSetup(AvvisendeController())
			.setControllerAdvice(RestExceptionHandler())
			.build()
		logCaptor = LogCaptor.forClass(RestExceptionHandler::class.java)
	}

	@AfterEach
	fun tearDown() = logCaptor.close()

	@Test
	fun `avvist tilgang gir 403 forbidden`() {
		mockMvc.get("/avvist").andExpect {
			status { isForbidden() }
			jsonPath("$.errorCode") { value("errorCode.forbidden") }
			jsonPath("$.message") { value("Autorisering feilet") }
		}
	}

	@Test
	fun `manglende autentisering gir også 403 forbidden`() {
		mockMvc.get("/uautentisert").andExpect {
			status { isForbidden() }
			jsonPath("$.errorCode") { value("errorCode.forbidden") }
		}
		assertTrue(logCaptor.errorLogs.isEmpty())
	}

	@RestController
	private class AvvisendeController {
		@GetMapping("/avvist", produces = [MediaType.APPLICATION_JSON_VALUE])
		fun avvist(): String = throw AuthorizationDeniedException("Access Denied")

		@GetMapping("/uautentisert", produces = [MediaType.APPLICATION_JSON_VALUE])
		fun uautentisert(): String = throw AuthenticationCredentialsNotFoundException("Ingen autentisering")
	}
}

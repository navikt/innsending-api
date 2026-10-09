package no.nav.soknad.innsending.exceptions

import nl.altindag.log.LogCaptor
import org.apache.catalina.connector.ClientAbortException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import kotlin.test.assertTrue

class RestExceptionHandlerTest {

	private lateinit var mockMvc: MockMvc
	private lateinit var logCaptor: LogCaptor

	@BeforeEach
	fun setUp() {
		mockMvc = MockMvcBuilders
			.standaloneSetup(DisconnectController(), IdentityController())
			.setControllerAdvice(RestExceptionHandler())
			.build()
		logCaptor = LogCaptor.forClass(RestExceptionHandler::class.java)
	}

	@AfterEach
	fun tearDown() {
		logCaptor.close()
	}

	@Test
	fun `invalid user identity produces a generic 401 and a safe reason category in logs`() {
		mockMvc.get("/identity/invalid")
			.andExpect {
				status { isUnauthorized() }
				jsonPath("$.message") { value("Autentisering feilet") }
				jsonPath("$.errorCode") { value("errorCode.unauthorized") }
			}

		assertTrue(logCaptor.warnLogs == listOf("Autentisering feilet: ugyldig brukerclaim"))
		assertTrue(logCaptor.errorLogs.isEmpty())
	}

	@Test
	fun `program errors remain server errors instead of becoming authentication failures`() {
		mockMvc.get("/identity/context-error")
			.andExpect {
				status { isInternalServerError() }
				jsonPath("$.errorCode") { value(ErrorCode.GENERAL_ERROR.code) }
			}
	}

	@Test
	fun `does not write an error response when Spring reports a disconnected client`() {
		mockMvc.get("/disconnect/async")
			.andExpect {
				status { isOk() }
				content { string("") }
			}

		assertTrue(logCaptor.errorLogs.isEmpty())
	}

	@Test
	fun `does not write an error response for a direct client abort`() {
		mockMvc.get("/disconnect/tomcat")
			.andExpect {
				status { isOk() }
				content { string("") }
			}

		assertTrue(logCaptor.errorLogs.isEmpty())
	}

	@RestController
	private class IdentityController {
		@GetMapping("/identity/invalid")
		fun invalidIdentity(): String = throw InvalidUserIdentityException()

		@GetMapping("/identity/context-error")
		fun contextError(): String = throw IllegalStateException("Missing request context")
	}

	@RestController
	private class DisconnectController {

		@GetMapping("/disconnect/async", produces = [MediaType.TEXT_PLAIN_VALUE])
		fun asyncDisconnect(): String {
			throw AsyncRequestNotUsableException("Client disconnected")
		}

		@GetMapping("/disconnect/tomcat", produces = [MediaType.TEXT_PLAIN_VALUE])
		fun tomcatDisconnect(): String {
			throw ClientAbortException("Client disconnected")
		}
	}
}

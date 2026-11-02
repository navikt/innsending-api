package no.nav.soknad.innsending.exceptions

import nl.altindag.log.LogCaptor
import no.nav.soknad.innsending.consumerapis.texas.TexasUnavailableException
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
			.standaloneSetup(DisconnectController())
			.setControllerAdvice(RestExceptionHandler())
			.build()
		logCaptor = LogCaptor.forClass(RestExceptionHandler::class.java)
	}

	@AfterEach
	fun tearDown() {
		logCaptor.close()
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

	@Test
	fun `feil mot Texas gir 503 og aldri 401 eller 403`() {
		mockMvc.get("/texas/unavailable")
			.andExpect {
				status { isServiceUnavailable() }
				jsonPath("$.errorCode") { value("temporarilyUnavailable") }
			}

		assertTrue(logCaptor.errorLogs.single().contains("Texas svarte 503"))
	}

	@RestController
	private class DisconnectController {

		@GetMapping("/texas/unavailable", produces = [MediaType.APPLICATION_JSON_VALUE])
		fun texasUnavailable(): String {
			throw TexasUnavailableException("Texas svarte 503 for identity_provider=entra_id target=api://x/.default")
		}

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

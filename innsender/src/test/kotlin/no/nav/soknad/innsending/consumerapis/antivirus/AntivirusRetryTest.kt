package no.nav.soknad.innsending.consumerapis.antivirus

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.CounterSnapshot
import io.prometheus.metrics.model.snapshots.HistogramSnapshot
import nl.altindag.log.LogCaptor
import no.nav.soknad.innsending.config.RetryConfiguration
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.service.AntivirusScanService
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import java.util.UUID
import javax.net.ssl.SSLHandshakeException
import kotlin.test.assertNotNull
import kotlin.test.assertIs

class AntivirusRetryTest {

	private val file = "document content".toByteArray()
	private val registry = PrometheusRegistry()
	private val metrics = InnsenderMetrics(registry)
	private val builder = RestClient.builder().baseUrl("http://antivirus")
	private val server = MockRestServiceServer.bindTo(builder).build()
	private val antivirus = AntivirusService(builder.build(), metrics)
	private val retry = Retry.of(
		"antivirus-test",
		RetryConfig.custom<AntivirusScanOutcome>()
			.maxAttempts(3)
			.intervalFunction { 0L }
			.retryOnResult { it.retryable }
			.retryOnException { false }
			.build(),
	)
	private val scanService = AntivirusScanService(antivirus, metrics, retry)
	private val clientLogs = LogCaptor.forClass(AntivirusService::class.java)
	private val scanLogs = LogCaptor.forClass(AntivirusScanService::class.java)

	@AfterEach
	fun tearDown() {
		clientLogs.close()
		scanLogs.close()
		Thread.interrupted()
	}

	@Test
	fun `should recover from HTTP 500 without emitting any error logs`() {
		expectRequest().andRespond(withStatus(HttpStatusCode.valueOf(500)).body("private error response"))
		expectRequest().andRespond(withSuccess(scanResponse("OK"), MediaType.APPLICATION_JSON))

		scanAsynchronously()

		server.verify()
		assertEquals(0, clientLogs.errorLogs.size)
		assertEquals(0, scanLogs.errorLogs.size)
		assertEquals(1, scanLogs.warnLogs.size)
		assertEquals(1.0, metrics.getAntivirusScanCounter("digital", "async", "ok"))
		assertEquals(0.0, metrics.getAntivirusScanCounter("digital", "async", "error"))
		assertEquals(1.0, metrics.getAntivirusScanAttemptCounter("digital", "error", "http_server_error"))
		assertEquals(1.0, metrics.getAntivirusScanAttemptCounter("digital", "ok", "none"))
		assertEquals(1.0, metrics.getAntivirusScanRetryCounter("digital", "http_server_error"))
		assertEquals(1.0, metrics.getAntivirusScanRecoveredCounter("digital", "ok"))
		assertFalse((clientLogs.logs + scanLogs.logs).any { it.contains("private error response") })
		val finalFields = scanLogs.logEvents.single { it.level == "INFO" }.keyValuePairs.associate { it.key to it.value }
		assertEquals(2, finalFields["scan_attempts"])
		assertEquals("ok", finalFields["scan_result"])
		assertEquals("none", finalFields["scan_failure_reason"])
		assertEquals(200, finalFields["http_status"])
		assertTrue(finalFields.containsKey("fileId"))
		assertTrue(finalFields.containsKey("scan_duration_ms"))
		val duration = assertIs<HistogramSnapshot>(
			registry.scrape().first { it.metadata.name == "innsendingapi_antivirus_scan_duration_seconds" },
		).dataPoints.single()
		assertEquals(1L, duration.count)
		assertEquals("digital", duration.labels.get("namespace"))
		assertEquals("ok", duration.labels.get("result"))
		assertTrue(duration.sum >= 0.0)
	}

	@Test
	fun `should produce exactly one alerting error after three HTTP failures`() {
		repeat(3) { expectRequest().andRespond(withStatus(HttpStatusCode.valueOf(503))) }

		scanAsynchronously()

		server.verify()
		assertEquals(0, clientLogs.errorLogs.size)
		assertEquals(1, scanLogs.errorLogs.size)
		assertEquals(2, scanLogs.warnLogs.size)
		assertEquals(1.0, metrics.getAntivirusScanCounter("digital", "async", "error"))
		assertEquals(3.0, metrics.getAntivirusScanAttemptCounter("digital", "error", "http_server_error"))
		assertEquals(2.0, metrics.getAntivirusScanRetryCounter("digital", "http_server_error"))
		val finalFields = scanLogs.logEvents.single { it.level == "ERROR" }.keyValuePairs.associate { it.key to it.value }
		assertEquals(3, finalFields["scan_attempts"])
		assertEquals("error", finalFields["scan_result"])
		assertEquals("http_server_error", finalFields["scan_failure_reason"])
		assertEquals(503, finalFields["http_status"])
	}

	@ParameterizedTest
	@ValueSource(ints = [500, 502, 503, 504])
	fun `should classify transient HTTP failures as retryable`(status: Int) {
		expectRequest().andRespond(withStatus(HttpStatusCode.valueOf(status)))

		val outcome = antivirus.scanAttempt(file)

		assertEquals(AntivirusScanResult.ERROR, outcome.result)
		assertEquals(status, outcome.httpStatus)
		assertEquals(AntivirusScanFailureReason.HTTP_SERVER_ERROR, outcome.failureReason)
		assertTrue(outcome.retryable)
		server.verify()
	}

	@ParameterizedTest
	@ValueSource(ints = [400, 401, 403, 404, 413, 429, 501, 505])
	fun `should not retry permanent HTTP failures`(status: Int) {
		expectRequest().andRespond(withStatus(HttpStatusCode.valueOf(status)))

		scanAsynchronously()

		server.verify()
		assertEquals(1, scanLogs.errorLogs.size)
		assertEquals(0, scanLogs.warnLogs.size)
		assertEquals(1.0, metrics.getAntivirusScanAttemptCounter("digital", "error", "http_error"))
		assertEquals(0.0, metrics.getAntivirusScanRetryCounter("digital", "http_error"))
	}

	@Test
	fun `should retry network timeouts and keep private exception messages out of logs`() {
		expectRequest().andRespond { throw SocketTimeoutException("private network diagnostic") }
		expectRequest().andRespond(withSuccess(scanResponse("OK"), MediaType.APPLICATION_JSON))

		scanAsynchronously()

		server.verify()
		assertEquals(0, scanLogs.errorLogs.size)
		assertEquals(1.0, metrics.getAntivirusScanRetryCounter("digital", "timeout"))
		assertEquals(1.0, metrics.getAntivirusScanRecoveredCounter("digital", "ok"))
		assertFalse((clientLogs.logs + scanLogs.logs).any { it.contains("private network diagnostic") })
	}

	@Test
	fun `should retry connection failures`() {
		expectRequest().andRespond { throw ConnectException("connection unavailable") }
		expectRequest().andRespond(withSuccess(scanResponse("OK"), MediaType.APPLICATION_JSON))

		scanAsynchronously()

		server.verify()
		assertEquals(0, scanLogs.errorLogs.size)
		assertEquals(1.0, metrics.getAntivirusScanRetryCounter("digital", "connection_error"))
		assertEquals(1.0, metrics.getAntivirusScanRecoveredCounter("digital", "ok"))
	}

	@ParameterizedTest
	@ValueSource(strings = ["timeout", "reset"])
	fun `should retry transport failures while reading the response body`(failure: String) {
		expectRequest().andRespond {
			MockClientHttpResponse(object : InputStream() {
				override fun read(): Int = if (failure == "timeout") {
					throw SocketTimeoutException("private response timeout")
				} else {
					throw SocketException("private connection reset")
				}
			}, HttpStatus.OK).apply { headers.contentType = MediaType.APPLICATION_JSON }
		}
		expectRequest().andRespond(withSuccess(scanResponse("OK"), MediaType.APPLICATION_JSON))

		scanAsynchronously()

		server.verify()
		assertEquals(0, scanLogs.errorLogs.size)
		assertEquals(1.0, metrics.getAntivirusScanRecoveredCounter("digital", "ok"))
		assertFalse((clientLogs.logs + scanLogs.logs).any { it.contains("private response") || it.contains("private connection") })
	}

	@Test
	fun `should not retry certificate validation failures`() {
		expectRequest().andRespond {
			throw SSLHandshakeException("private certificate diagnostic").apply {
				initCause(CertificateException("private certificate cause"))
			}
		}

		scanAsynchronously()

		server.verify()
		assertEquals(1, scanLogs.errorLogs.size)
		assertEquals(1.0, metrics.getAntivirusScanAttemptCounter("digital", "error", "connection_rejected"))
		assertEquals(0.0, metrics.getAntivirusScanRetryCounter("digital", "connection_rejected"))
		val fields = scanLogs.logEvents.single { it.level == "ERROR" }.keyValuePairs.associate { it.key to it.value }
		assertEquals("CertificateException", fields["exception_type"])
		assertFalse((clientLogs.logs + scanLogs.logs).any { it.contains("private certificate") })
	}

	@ParameterizedTest
	@ValueSource(strings = ["", "[]", "[{},{}]", "not json"])
	fun `should report malformed responses without retrying or logging their bodies`(body: String) {
		expectRequest().andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

		scanAsynchronously()

		server.verify()
		assertEquals(1, scanLogs.errorLogs.size)
		assertEquals(1.0, metrics.getAntivirusScanAttemptCounter("digital", "error", "invalid_response"))
		assertEquals(0.0, metrics.getAntivirusScanRetryCounter("digital", "invalid_response"))
	}

	@Test
	fun `should not retry a scanner error returned in an HTTP 200 response`() {
		expectRequest().andRespond(withSuccess(scanResponse("ERROR"), MediaType.APPLICATION_JSON))

		scanAsynchronously()

		server.verify()
		assertEquals(1, scanLogs.errorLogs.size)
		assertEquals(1.0, metrics.getAntivirusScanAttemptCounter("digital", "error", "scanner_error"))
		assertEquals(0.0, metrics.getAntivirusScanRetryCounter("digital", "scanner_error"))
	}

	@Test
	fun `should not retry or log the filename when a virus is found`() {
		expectRequest().andRespond(withSuccess(scanResponse("FOUND"), MediaType.APPLICATION_JSON))

		scanAsynchronously()

		server.verify()
		assertEquals(1, scanLogs.warnLogs.size)
		assertEquals(0, scanLogs.errorLogs.size)
		assertEquals(1.0, metrics.getAntivirusScanCounter("digital", "async", "found"))
		assertFalse((clientLogs.logs + scanLogs.logs).any { it.contains("private filename") })
	}

	@Test
	fun `should preserve synchronous HTTP failure behaviour without retries`() {
		expectRequest().andRespond(withStatus(HttpStatusCode.valueOf(500)))

		assertThrows<IllegalActionException> {
			scanService.scanSynchronously(FileStorageNamespace.NOLOGIN, file, UUID.randomUUID(), "M1")
		}

		server.verify()
		assertEquals(1.0, metrics.getAntivirusScanCounter("nologin", "sync", "error"))
		assertEquals(1, scanLogs.errorLogs.size)
		assertEquals(0, clientLogs.errorLogs.size)
	}

	@Test
	fun `should preserve synchronous transport exceptions`() {
		expectRequest().andRespond { throw SocketTimeoutException("network timeout") }

		assertThrows<ResourceAccessException> { antivirus.scan(file) }

		server.verify()
	}

	@Test
	fun `should configure three attempts with increasing randomized exponential backoff`() {
		val config = RetryConfiguration().retryAntivirus().retryConfig
		val intervalFunction = assertNotNull(config.intervalFunction)

		assertEquals(3, config.maxAttempts)
		repeat(20) {
			assertTrue(intervalFunction.apply(1) in 1_500L..2_500L)
			assertTrue(intervalFunction.apply(2) in 3_000L..5_000L)
		}
		assertFalse(config.exceptionPredicate.test(RuntimeException()))
	}

	@Test
	fun `should export zero-valued counters before the first failure or recovery`() {
		val snapshots = registry.scrape()
		val retries = assertIs<CounterSnapshot>(
			snapshots.first { it.metadata.name.removeSuffix("_total") == "innsendingapi_antivirus_scan_retries" },
		)
		assertTrue(retries.dataPoints.any {
			it.labels.get("namespace") == "digital" && it.labels.get("reason") == "http_server_error" && it.value == 0.0
		})
		val recovered = assertIs<CounterSnapshot>(
			snapshots.first { it.metadata.name.removeSuffix("_total") == "innsendingapi_antivirus_scan_recovered" },
		)
		assertTrue(recovered.dataPoints.any {
			it.labels.get("namespace") == "digital" && it.labels.get("result") == "ok" && it.value == 0.0
		})
	}

	private fun expectRequest() = server.expect(requestTo("http://antivirus/scan"))
		.andExpect(method(HttpMethod.PUT))
		.andExpect(content().bytes(file))

	private fun scanResponse(result: String) =
		"""[{"Filename":"private filename","Result":"$result"}]"""

	private fun scanAsynchronously() = scanService.scanAsynchronously(
		FileStorageNamespace.DIGITAL, file, UUID.randomUUID(), "M1", UUID.randomUUID().toString(),
	)
}

package no.nav.soknad.innsending.service.unittest

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nl.altindag.log.LogCaptor
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusInterface
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusScanFailureReason
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusScanOutcome
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusScanResult
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.service.AntivirusScanMode
import no.nav.soknad.innsending.service.AntivirusScanService
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertTrue

class AntivirusScanServiceUnitTest {

	private val antivirus = mockk<AntivirusInterface>()
	private val innsenderMetrics = mockk<InnsenderMetrics>(relaxed = true)
	private val backoffAttempts = mutableListOf<Int>()
	private val retry = Retry.of(
		"antivirus-test",
		RetryConfig.custom<AntivirusScanOutcome>()
			.maxAttempts(3)
			.intervalFunction { attempt -> backoffAttempts.add(attempt); 0L }
			.retryOnResult { it.retryable }
			.retryOnException { false }
			.build(),
	)
	private val antivirusScanService = AntivirusScanService(antivirus, innsenderMetrics, retry)
	private val logCaptor = LogCaptor.forClass(AntivirusScanService::class.java)

	@AfterEach
	fun tearDown() {
		logCaptor.close()
		Thread.interrupted()
	}

	@Test
	fun `should throw for synchronous virus findings and emit metric`() {
		every { antivirus.scan(any()) } returns AntivirusScanResult.FOUND

		assertThrows<IllegalActionException> {
			antivirusScanService.scanSynchronously(
				namespace = FileStorageNamespace.NOLOGIN,
				fileContent = "test".toByteArray(),
				innsendingsId = UUID.randomUUID(),
				attachmentId = "M1",
			)
		}

		verify {
			innsenderMetrics.incAntivirusScanCounter(
				FileStorageNamespace.NOLOGIN.value,
				AntivirusScanMode.SYNCHRONOUS.value,
				AntivirusScanResult.FOUND.metricValue,
			)
		}
	}

	@Test
	fun `should log warning and emit metric for asynchronous virus findings`() {
		every { antivirus.scanAttempt(any()) } returns AntivirusScanOutcome(AntivirusScanResult.FOUND)

		antivirusScanService.scanAsynchronously(
			namespace = FileStorageNamespace.DIGITAL,
			fileContent = "test".toByteArray(),
			innsendingsId = UUID.randomUUID(),
			attachmentId = "M1",
			fileId = "file-123",
		)

		verify {
			innsenderMetrics.incAntivirusScanCounter(
				FileStorageNamespace.DIGITAL.value,
				AntivirusScanMode.ASYNCHRONOUS.value,
				AntivirusScanResult.FOUND.metricValue,
			)
		}
		assertTrue(logCaptor.warnLogs.any { it.contains("fant virus") })
		assertEquals(0, logCaptor.errorLogs.size)
		verify(exactly = 1) { antivirus.scanAttempt(any()) }
		assertTrue(backoffAttempts.isEmpty())
	}

	@Test
	fun `should log one error without exception details when asynchronous scan throws`() {
		every { antivirus.scanAttempt(any()) } throws RuntimeException("private diagnostic")

		antivirusScanService.scanAsynchronously(
			namespace = FileStorageNamespace.DIGITAL,
			fileContent = "test".toByteArray(),
			innsendingsId = UUID.randomUUID(),
			attachmentId = "M1",
			fileId = "file-123",
		)

		verify {
			innsenderMetrics.incAntivirusScanCounter(
				FileStorageNamespace.DIGITAL.value,
				AntivirusScanMode.ASYNCHRONOUS.value,
				AntivirusScanResult.ERROR.metricValue,
			)
		}
		assertEquals(1, logCaptor.errorLogs.size)
		assertTrue(logCaptor.errorLogs.single().contains("mislyktes"))
		assertTrue(logCaptor.logs.none { it.contains("private diagnostic") })
		verify(exactly = 1) { antivirus.scanAttempt(any()) }
		assertTrue(backoffAttempts.isEmpty())
	}

	@Test
	fun `should recover after exponential retries without error alerts and count one final outcome`() {
		every { antivirus.scanAttempt(any()) } returnsMany listOf(
			transientFailure(),
			transientFailure(),
			AntivirusScanOutcome(AntivirusScanResult.OK),
		)

		scanAsynchronously()

		verify(exactly = 3) { antivirus.scanAttempt(any()) }
		verify(exactly = 2) { innsenderMetrics.incAntivirusScanAttemptCounter("digital", "error", "http_server_error") }
		verify(exactly = 1) { innsenderMetrics.incAntivirusScanAttemptCounter("digital", "ok", "none") }
		verify(exactly = 2) { innsenderMetrics.incAntivirusScanRetryCounter("digital", "http_server_error") }
		verify(exactly = 1) { innsenderMetrics.incAntivirusScanCounter("digital", "async", "ok") }
		verify(exactly = 0) { innsenderMetrics.incAntivirusScanCounter("digital", "async", "error") }
		verify(exactly = 1) { innsenderMetrics.incAntivirusScanRecoveredCounter("digital", "ok") }
		verify(exactly = 1) { innsenderMetrics.observeAntivirusScanDuration("digital", "ok", any()) }
		assertEquals(listOf(1, 2), backoffAttempts)
		assertEquals(2, logCaptor.warnLogs.size)
		assertEquals(0, logCaptor.errorLogs.size)
		assertTrue(logCaptor.infoLogs.single().contains("bestått etter retry"))
	}

	@Test
	fun `should emit one error only after all three transient attempts fail`() {
		every { antivirus.scanAttempt(any()) } returns transientFailure()

		scanAsynchronously()

		verify(exactly = 3) { antivirus.scanAttempt(any()) }
		verify(exactly = 3) { innsenderMetrics.incAntivirusScanAttemptCounter("digital", "error", "http_server_error") }
		verify(exactly = 2) { innsenderMetrics.incAntivirusScanRetryCounter("digital", "http_server_error") }
		verify(exactly = 1) { innsenderMetrics.incAntivirusScanCounter("digital", "async", "error") }
		verify(exactly = 0) { innsenderMetrics.incAntivirusScanRecoveredCounter(any(), any()) }
		verify(exactly = 1) { innsenderMetrics.observeAntivirusScanDuration("digital", "error", any()) }
		assertEquals(listOf(1, 2), backoffAttempts)
		assertEquals(2, logCaptor.warnLogs.size)
		assertEquals(1, logCaptor.errorLogs.size)
	}

	@Test
	fun `should not retry a scanner error returned with a successful HTTP response`() {
		every { antivirus.scanAttempt(any()) } returns AntivirusScanOutcome(AntivirusScanResult.ERROR)

		scanAsynchronously()

		verify(exactly = 1) { antivirus.scanAttempt(any()) }
		verify(exactly = 1) { innsenderMetrics.incAntivirusScanCounter("digital", "async", "error") }
		assertTrue(backoffAttempts.isEmpty())
		assertEquals(1, logCaptor.errorLogs.size)
	}

	@Test
	fun `should stop retrying once a virus is found`() {
		every { antivirus.scanAttempt(any()) } returnsMany listOf(
			transientFailure(),
			AntivirusScanOutcome(AntivirusScanResult.FOUND),
		)

		scanAsynchronously()

		verify(exactly = 2) { antivirus.scanAttempt(any()) }
		verify(exactly = 1) { innsenderMetrics.incAntivirusScanCounter("digital", "async", "found") }
		verify(exactly = 1) { innsenderMetrics.incAntivirusScanRecoveredCounter("digital", "found") }
		assertEquals(listOf(1), backoffAttempts)
		assertEquals(0, logCaptor.errorLogs.size)
		assertTrue(logCaptor.warnLogs.any { it.contains("fant virus") })
	}

	@Test
	fun `should preserve synchronous failure behaviour without retries`() {
		every { antivirus.scan(any()) } returns AntivirusScanResult.ERROR

		assertThrows<IllegalActionException> {
			antivirusScanService.scanSynchronously(FileStorageNamespace.NOLOGIN, byteArrayOf(1), UUID.randomUUID(), "M1")
		}

		verify(exactly = 1) { antivirus.scan(any()) }
		verify(exactly = 0) { antivirus.scanAttempt(any()) }
		verify(exactly = 1) { innsenderMetrics.incAntivirusScanCounter("nologin", "sync", "error") }
		assertTrue(backoffAttempts.isEmpty())
		assertEquals(1, logCaptor.errorLogs.size)
	}

	@Test
	fun `should preserve synchronous exceptions and register their outcome once`() {
		every { antivirus.scan(any()) } throws IllegalStateException("boom")

		assertThrows<IllegalStateException> {
			antivirusScanService.scanSynchronously(FileStorageNamespace.NOLOGIN, byteArrayOf(1), UUID.randomUUID(), "M1")
		}

		verify(exactly = 1) { antivirus.scan(any()) }
		verify(exactly = 1) { innsenderMetrics.incAntivirusScanCounter("nologin", "sync", "error") }
		assertTrue(backoffAttempts.isEmpty())
	}

	@Test
	fun `should cancel an interrupted backoff without further requests or error alerts`() {
		every { antivirus.scanAttempt(any()) } answers {
			Thread.currentThread().interrupt()
			transientFailure()
		}

		scanAsynchronously()

		assertTrue(Thread.currentThread().isInterrupted)
		verify(exactly = 1) { antivirus.scanAttempt(any()) }
		verify(exactly = 1) { innsenderMetrics.incAntivirusScanCounter("digital", "async", "error") }
		verify(exactly = 0) { innsenderMetrics.incAntivirusScanRetryCounter(any(), any()) }
		assertEquals(0, logCaptor.errorLogs.size)
		assertTrue(logCaptor.warnLogs.any { it.contains("avbrutt") })
	}

	private fun transientFailure() = AntivirusScanOutcome(
		AntivirusScanResult.ERROR, AntivirusScanFailureReason.HTTP_SERVER_ERROR, 500,
	)

	private fun scanAsynchronously() = antivirusScanService.scanAsynchronously(
		FileStorageNamespace.DIGITAL, byteArrayOf(1), UUID.randomUUID(), "M1", "file-123",
	)
}

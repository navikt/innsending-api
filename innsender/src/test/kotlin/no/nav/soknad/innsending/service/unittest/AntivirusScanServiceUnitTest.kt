package no.nav.soknad.innsending.service.unittest

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nl.altindag.log.LogCaptor
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusInterface
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusScanResult
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.service.AntivirusScanMode
import no.nav.soknad.innsending.service.AntivirusScanService
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertTrue

class AntivirusScanServiceUnitTest {

	private val antivirus = mockk<AntivirusInterface>()
	private val innsenderMetrics = mockk<InnsenderMetrics>(relaxed = true)
	private val antivirusScanService = AntivirusScanService(antivirus, innsenderMetrics)
	private val logCaptor = LogCaptor.forClass(AntivirusScanService::class.java)

	@AfterEach
	fun tearDown() {
		logCaptor.clearLogs()
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
		every { antivirus.scan(any()) } returns AntivirusScanResult.FOUND

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
	}

	@Test
	fun `should log warning and emit error metric when asynchronous scan throws`() {
		every { antivirus.scan(any()) } throws RuntimeException("boom")

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
		assertTrue(logCaptor.warnLogs.any { it.contains("kastet feil") })
	}
}

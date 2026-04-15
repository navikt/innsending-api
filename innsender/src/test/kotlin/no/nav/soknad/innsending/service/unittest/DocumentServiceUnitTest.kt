package no.nav.soknad.innsending.service.unittest

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.service.AntivirusScanService
import no.nav.soknad.innsending.service.DocumentService
import no.nav.soknad.innsending.service.FilValidatorService
import no.nav.soknad.innsending.service.ValidatedFile
import no.nav.soknad.innsending.service.fillager.FilMetadata
import no.nav.soknad.innsending.service.fillager.FileStorage
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.service.fillager.FilStatus
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.pdfutilities.KonverterTilPdfInterface
import no.nav.soknad.pdfutilities.PdfMerger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.io.ByteArrayResource
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(MockKExtension::class)
class DocumentServiceUnitTest {

	@RelaxedMockK
	lateinit var fileStorage: FileStorage

	@RelaxedMockK
	lateinit var filValidatorService: FilValidatorService

	@RelaxedMockK
	lateinit var antivirusScanService: AntivirusScanService

	@RelaxedMockK
	lateinit var konverterTilPdf: KonverterTilPdfInterface

	@RelaxedMockK
	lateinit var innsenderMetrics: InnsenderMetrics

	@RelaxedMockK
	lateinit var pdfMerger: PdfMerger

	@InjectMockKs
	lateinit var documentService: DocumentService

	@Test
	fun `should force synchronous antivirus scan for nologin uploads`() {
		val innsendingId = UUID.randomUUID()
		val resource = testResource()
		val validatedFile = ValidatedFile(resource.byteArray, ".pdf")

		every { filValidatorService.validateFile(resource, innsendingId.toString()) } returns validatedFile
		every { konverterTilPdf.tilPdf(any(), any(), any(), any(), any()) } returns (resource.byteArray to 1)
		every { fileStorage.save(any(), any(), any()) } returns fileMetadata(innsendingId)

		documentService.saveAttachment(
			namespace = FileStorageNamespace.NOLOGIN,
			fil = resource,
			vedleggId = "M1",
			innsendingsId = innsendingId,
		)

		verify(exactly = 1) {
			antivirusScanService.scanSynchronously(
				FileStorageNamespace.NOLOGIN,
				resource.byteArray,
				innsendingId,
				"M1",
			)
		}
		verify(exactly = 0) { antivirusScanService.scanAsynchronously(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `should dispatch asynchronous antivirus scan for digital uploads`() {
		val innsendingId = UUID.randomUUID()
		val resource = testResource()
		val validatedFile = ValidatedFile(resource.byteArray, ".pdf")

		every { filValidatorService.validateFile(resource, innsendingId.toString()) } returns validatedFile
		every { konverterTilPdf.tilPdf(any(), any(), any(), any(), any()) } returns (resource.byteArray to 1)
		every { fileStorage.save(any(), any(), any()) } returns fileMetadata(innsendingId)

		documentService.saveAttachment(
			namespace = FileStorageNamespace.DIGITAL,
			fil = resource,
			vedleggId = "M1",
			innsendingsId = innsendingId,
		)

		verify(exactly = 0) { antivirusScanService.scanSynchronously(any(), any(), any(), any()) }
		verify(exactly = 1) {
			antivirusScanService.scanAsynchronously(
				FileStorageNamespace.DIGITAL,
				resource.byteArray,
				innsendingId,
				"M1",
				any(),
			)
		}
	}

	private fun testResource(): ByteArrayResource {
		val bytes = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
		return object : ByteArrayResource(bytes) {
			override fun getFilename(): String = "litenPdf.pdf"
		}
	}

	private fun fileMetadata(innsendingId: UUID) = FilMetadata(
		filId = UUID.randomUUID().toString(),
		vedleggId = "M1",
		innsendingId = innsendingId.toString(),
		filnavn = "litenPdf.pdf",
		storrelse = 123,
		filtype = ".pdf",
		status = FilStatus.LASTET_OPP,
		mimetype = Mimetype.applicationSlashPdf,
		createdAt = OffsetDateTime.now(),
	)
}

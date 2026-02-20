package no.nav.soknad.innsending.service

import io.mockk.InternalPlatformDsl.toStr
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.service.fillager.FilStatus
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.util.stringextensions.toUUID
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ByteArrayResource
import java.util.UUID
import kotlin.test.assertContains

class DocumentServiceTest : ApplicationTest() {

	@Autowired
	lateinit var documentService: DocumentService

	@Test
	fun `should store and retrieve file`() {
		val innsendingId = UUID.randomUUID()
		val resource = loadResource("/litenPdf.pdf")

		val metadata = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource, "N6", innsendingId)
		assertNotNull(metadata.filId)
		assertEquals("N6", metadata.vedleggId)
		assertEquals(innsendingId.toString(), metadata.innsendingId)

		val fil =
			documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId, metadata.filId.toUUID())
		assertNotNull(fil)
		assertArrayEquals(resource.byteArray, fil?.innhold)
		assertEquals("litenPdf.pdf", fil?.metadata?.filnavn)
	}

	@Test
	fun `should delete file`() {
		val innsendingId = UUID.randomUUID()
		val resource = loadResource("/litenPdf.pdf")

		val metadata = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource, "N7", innsendingId)
		val deleted = documentService.deleteAttachment(
			FileStorageNamespace.NOLOGIN,
			innsendingId,
			metadata.vedleggId,
			metadata.filId.toUUID()
		)
		assertTrue(deleted)

		val fil =
			documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId, metadata.filId.toUUID())
		assertNull(fil)
	}

	@Test
	fun `should return null for non-existent file`() {
		val fil = documentService.getFile(
			FileStorageNamespace.NOLOGIN,
			UUID.randomUUID(),
			UUID.randomUUID()
		)
		assertNull(fil)
	}

	@Test
	fun `should handle deleting already deleted file`() {
		val innsendingId = UUID.randomUUID()
		val resource = loadResource("/litenPdf.pdf")

		val metadata = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource, "N9", innsendingId)
		documentService.deleteAttachment(FileStorageNamespace.NOLOGIN, innsendingId, null, metadata.filId.toUUID())

		val deletedAgain =
			documentService.deleteAttachment(FileStorageNamespace.NOLOGIN, innsendingId, null, metadata.filId.toUUID())
		assertFalse(deletedAgain) // File should not exist anymore
	}

	@Test
	fun `should handle storing empty file`() {
		val innsendingId = UUID.randomUUID()
		val resource = ByteArrayResource(ByteArray(0)) // Empty file

		val exception = assertThrows(IllegalActionException::class.java) {
			documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource, "N8", innsendingId)
		}
		assertEquals("Opplasting feilet. Filen er tom", exception.message)
	}

	@Test
	fun `should handle storing large file`() {
		val innsendingId = UUID.randomUUID()
		val resource = loadResource("/mellomstor.pdf")

		val metadata = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource, "N10", innsendingId)
		assertNotNull(metadata.filId)
		assertEquals(resource.byteArray.size, metadata.storrelse)
	}

	@Test
	fun `should delete all files for an innsending`() {
		val innsendingId = UUID.randomUUID()
		val resource1 = loadResource("/litenPdf.pdf")
		val resource2 = loadResource("/mellomstor.pdf")

		val lagretFil1 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource1, "N11", innsendingId)
		val lagretFil2 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource2, "N12", innsendingId)

		val deleted = documentService.deleteAttachment(FileStorageNamespace.NOLOGIN, innsendingId)
		assertTrue(deleted)

		val fil1 = documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId, lagretFil1.filId.toUUID())
		val fil2 = documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId, lagretFil2.filId.toUUID())

		assertNull(fil1)
		assertNull(fil2)
	}

	@Test
	fun `should delete all files for a given attachment id`() {
		val innsendingId = UUID.randomUUID()
		val resource1 = loadResource("/litenPdf.pdf")

		val lagretFil1 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource1, "N11", innsendingId)
		val lagretFil2 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource1, "N11", innsendingId)
		val lagretFil3 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource1, "N13", innsendingId)

		val deleted = documentService.deleteAttachment(FileStorageNamespace.NOLOGIN, innsendingId, "N11")
		assertTrue(deleted)

		val fil1 = documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId, lagretFil1.filId.toUUID())
		val fil2 = documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId, lagretFil2.filId.toUUID())
		val fil3 = documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId, lagretFil3.filId.toUUID())

		assertNull(fil1)
		assertNull(fil2)
		assertNotNull(fil3)
	}

	@Test
	fun `should save alternative main document`() {
		val innsendingId = UUID.randomUUID()
		val resource1 = loadResource("/sanity.json")

		val file = documentService.saveMainDocument(
			FileStorageNamespace.NOLOGIN,
			innsendingId,
			resource1,
			"NAV 10-00.00",
			Mimetype.applicationSlashJson,
		)
		assertEquals(Mimetype.applicationSlashJson, file.mimetype)
		assertNotNull(file.filId)
		assertEquals(FilStatus.LASTET_OPP, file.status)
		assertEquals(".json", file.filtype)
		assertTrue(file.filnavn.endsWith(".json"))
	}

	@Test
	fun `should fail when saving main document with illegal mimetype`() {
		val innsendingId = UUID.randomUUID()
		val resource1 = loadResource("/sanity.json")

		val exception = assertThrows(IllegalArgumentException::class.java) {
			documentService.saveMainDocument(
				FileStorageNamespace.NOLOGIN,
				innsendingId,
				resource1,
				"NAV 10-00.00",
				Mimetype.imageSlashJpeg,
			)
		}
		assertTrue(exception.message?.startsWith("Ugyldig mimetype for hoveddokument") == true)
	}

	@Test
	fun `should merge two files`() {
		val innsendingId = UUID.randomUUID()
		val attachmentId = "N14"
		val resource1 = loadResource("/litenPdf.pdf")
		val resource2 = loadResource("/litenPdf.pdf")

		val lagretFil1 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource1, attachmentId, innsendingId)
		val lagretFil2 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource2, attachmentId, innsendingId)

		val mergedContent = documentService.mergeFiles(
			FileStorageNamespace.NOLOGIN,
			innsendingId,
			listOf(lagretFil1.filId.toUUID(), lagretFil2.filId.toUUID())
		)
		assertNotNull(mergedContent)
		assertTrue(mergedContent!!.isNotEmpty())
	}

	@Test
	fun `should return the only file on merge request`() {
		val innsendingId = UUID.randomUUID()
		val attachmentId = "N14"
		val resource1 = loadResource("/litenPdf.pdf")

		val lagretFil1 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource1, attachmentId, innsendingId)

		val mergedContent = documentService.mergeFiles(
			FileStorageNamespace.NOLOGIN,
			innsendingId,
			listOf(lagretFil1.filId.toUUID())
		)
		assertNotNull(mergedContent)
		assertEquals(lagretFil1.storrelse, mergedContent!!.size)
	}

	@Test
	fun `should fail if any file has other mimetype than pdf`() {
		val innsendingId = UUID.randomUUID()
		val attachmentId = "N14"
		val resource1 = loadResource("/litenPdf.pdf")
		val resource2 = loadResource("/sanity.json")

		val lagretFil1 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource1, attachmentId, innsendingId)
		val lagretFil2 = documentService.saveMainDocument(FileStorageNamespace.NOLOGIN, innsendingId, resource2, "NAV 10-00.00", Mimetype.applicationSlashJson)

		val exception = assertThrows(IllegalStateException::class.java) {
			documentService.mergeFiles(
				FileStorageNamespace.NOLOGIN,
				innsendingId,
				listOf(lagretFil1.filId.toUUID(), lagretFil2.filId.toUUID())
			)
		}
		assertEquals("Alle filer må være PDF for å kunne merges", exception.message)
	}

	@Test
	fun `should fail if any file is missing`() {
		val innsendingId = UUID.randomUUID()
		val attachmentId = "N14"
		val resource1 = loadResource("/litenPdf.pdf")

		val lagretFil1 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource1, attachmentId, innsendingId)
		val ulagretFil2Id = UUID.randomUUID()
		val exception = assertThrows(ResourceNotFoundException::class.java) {
			documentService.mergeFiles(
				FileStorageNamespace.NOLOGIN,
				innsendingId,
				listOf(lagretFil1.filId.toUUID(), ulagretFil2Id)
			)
		}
		assertEquals("Finner ikke alle filer, 1 mangler", exception.message)
	}

	@Test
	fun `should include soft-deleted file in merge`() {
		val innsendingId = UUID.randomUUID()
		val attachmentId = "N14"
		val resource1 = loadResource("/litenPdf.pdf")
		val resource2 = loadResource("/litenPdf.pdf")

		val lagretFil1 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource1, attachmentId, innsendingId)
		val lagretFil2 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource2, attachmentId, innsendingId)
		documentService.deleteAttachment(FileStorageNamespace.NOLOGIN, innsendingId, attachmentId, lagretFil2.filId.toUUID())

		val mergedContent = documentService.mergeFiles(
			FileStorageNamespace.NOLOGIN,
			innsendingId,
			listOf(lagretFil1.filId.toUUID(), lagretFil2.filId.toUUID())
		)
		assertNotNull(mergedContent)
	}

	private fun loadResource(filnavn: String): ByteArrayResource {
		return object : ByteArrayResource(Hjelpemetoder.Companion.getBytesFromFile(filnavn)) {
			override fun getFilename(): String = filnavn.removePrefix("/")
		}
	}

}

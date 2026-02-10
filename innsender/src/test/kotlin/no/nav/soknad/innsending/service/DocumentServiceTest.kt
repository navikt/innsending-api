package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.util.stringextensions.toUUID
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ByteArrayResource
import java.util.UUID

class DocumentServiceTest : ApplicationTest() {

	@Autowired
	lateinit var documentService: DocumentService

	@Test
	fun `should store and retrieve file`() {
		val innsendingId = UUID.randomUUID()
		val resource = loadResource("/litenPdf.pdf")

		val metadata = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource, "N6", innsendingId)
		Assertions.assertNotNull(metadata.filId)
		Assertions.assertEquals("N6", metadata.vedleggId)
		Assertions.assertEquals(innsendingId.toString(), metadata.innsendingId)

		val fil =
			documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId, metadata.filId.toUUID())
		Assertions.assertNotNull(fil)
		Assertions.assertArrayEquals(resource.byteArray, fil?.innhold)
		Assertions.assertEquals("litenPdf.pdf", fil?.metadata?.filnavn)
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
		Assertions.assertTrue(deleted)

		val fil =
			documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId, metadata.filId.toUUID())
		Assertions.assertNull(fil)
	}

	@Test
	fun `should return null for non-existent file`() {
		val fil = documentService.getFile(
			FileStorageNamespace.NOLOGIN,
			UUID.randomUUID(),
			UUID.randomUUID()
		)
		Assertions.assertNull(fil)
	}

	@Test
	fun `should handle deleting already deleted file`() {
		val innsendingId = UUID.randomUUID()
		val resource = loadResource("/litenPdf.pdf")

		val metadata = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource, "N9", innsendingId)
		documentService.deleteAttachment(FileStorageNamespace.NOLOGIN, innsendingId,null,metadata.filId.toUUID())

		val deletedAgain = documentService.deleteAttachment(FileStorageNamespace.NOLOGIN, innsendingId,null,metadata.filId.toUUID())
		Assertions.assertFalse(deletedAgain) // File should not exist anymore
	}

	@Test
	fun `should handle storing empty file`() {
		val innsendingId = UUID.randomUUID()
		val resource = ByteArrayResource(ByteArray(0)) // Empty file

		val exception = Assertions.assertThrows(IllegalActionException::class.java) {
			documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource, "N8", innsendingId)
		}
		Assertions.assertEquals("Opplasting feilet. Filen er tom", exception.message)
	}

	@Test
	fun `should handle storing large file`() {
		val innsendingId = UUID.randomUUID()
		val resource = loadResource("/mellomstor.pdf")

		val metadata = documentService.saveAttachment(FileStorageNamespace.NOLOGIN,resource, "N10", innsendingId)
		Assertions.assertNotNull(metadata.filId)
		Assertions.assertEquals(resource.byteArray.size, metadata.storrelse)
	}

	@Test
	fun `should delete all files for an innsending`() {
		val innsendingId = UUID.randomUUID()
		val resource1 = loadResource("/litenPdf.pdf")
		val resource2 = loadResource("/mellomstor.pdf")

		val lagretFil1 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN,resource1, "N11", innsendingId)
		val lagretFil2 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource2, "N12", innsendingId)

		val deleted = documentService.deleteAttachment(FileStorageNamespace.NOLOGIN, innsendingId)
		Assertions.assertTrue(deleted)

		val fil1 = documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId, lagretFil1.filId.toUUID())
		val fil2 = documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId,lagretFil2.filId.toUUID())

		Assertions.assertNull(fil1)
		Assertions.assertNull(fil2)
	}

	@Test
	fun `should delete all files for a given attachment id`() {
		val innsendingId = UUID.randomUUID()
		val resource1 = loadResource("/litenPdf.pdf")

		val lagretFil1 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN,resource1, "N11", innsendingId)
		val lagretFil2 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource1, "N11", innsendingId)
		val lagretFil3 = documentService.saveAttachment(FileStorageNamespace.NOLOGIN, resource1, "N13", innsendingId)

		val deleted = documentService.deleteAttachment(FileStorageNamespace.NOLOGIN, innsendingId, "N11")
		Assertions.assertTrue(deleted)

		val fil1 = documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId, lagretFil1.filId.toUUID())
		val fil2 = documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId,lagretFil2.filId.toUUID())
		val fil3 = documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingId,lagretFil3.filId.toUUID())

		Assertions.assertNull(fil1)
		Assertions.assertNull(fil2)
		Assertions.assertNotNull(fil3)
	}

	private fun loadResource(filnavn: String): ByteArrayResource {
		return object : ByteArrayResource(Hjelpemetoder.Companion.getBytesFromFile(filnavn)) {
			override fun getFilename(): String = filnavn.removePrefix("/")
		}
	}

}

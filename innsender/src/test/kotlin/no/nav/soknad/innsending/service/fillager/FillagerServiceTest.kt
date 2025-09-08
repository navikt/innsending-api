package no.nav.soknad.innsending.service.fillager

import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Assertions.*
import org.springframework.core.io.ByteArrayResource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class FillagerServiceTest : ApplicationTest() {

	@Autowired
	lateinit var fillagerService: FillagerService

	@Test
	fun `should store and retrieve file`() {
		val innsendingId = UUID.randomUUID().toString()
		val resource = loadResource("/litenPdf.pdf")

		val metadata = fillagerService.lagreFil(resource, "N6", innsendingId, FillagerNamespace.NOLOGIN)
		assertNotNull(metadata.filId)
		assertEquals("N6", metadata.vedleggId)
		assertEquals(innsendingId, metadata.innsendingId)

		val fil = fillagerService.hentFil(metadata.filId, innsendingId, FillagerNamespace.NOLOGIN)
		assertNotNull(fil)
		assertArrayEquals(resource.byteArray, fil!!.innhold)
		assertEquals("litenPdf.pdf", fil.metadata.filnavn)
	}

	@Test
	fun `should delete file`() {
		val innsendingId = UUID.randomUUID().toString()
		val resource = loadResource("/litenPdf.pdf")

		val metadata = fillagerService.lagreFil(resource, "N7", innsendingId, FillagerNamespace.NOLOGIN)
		val deleted = fillagerService.slettFil(metadata.filId, innsendingId, FillagerNamespace.NOLOGIN)
		assertTrue(deleted)

		val fil = fillagerService.hentFil(metadata.filId, innsendingId, FillagerNamespace.NOLOGIN)
		assertNull(fil)
	}

	@Test
	fun `should return null for non-existent file`() {
		val fil = fillagerService.hentFil("non-existent-id", "non-existent-innsending", FillagerNamespace.NOLOGIN)
		assertNull(fil)
	}

	@Test
	fun `should handle deleting already deleted file`() {
		val innsendingId = UUID.randomUUID().toString()
		val resource = loadResource("/litenPdf.pdf")

		val metadata = fillagerService.lagreFil(resource, "N9", innsendingId, FillagerNamespace.NOLOGIN)
		fillagerService.slettFil(metadata.filId, innsendingId, FillagerNamespace.NOLOGIN)

		val deletedAgain = fillagerService.slettFil(metadata.filId, innsendingId, FillagerNamespace.NOLOGIN)
		assertFalse(deletedAgain) // File should not exist anymore
	}

	@Test
	fun `should handle storing empty file`() {
		val innsendingId = UUID.randomUUID().toString()
		val resource = ByteArrayResource(ByteArray(0)) // Empty file

		val exception = assertThrows(no.nav.soknad.innsending.exceptions.IllegalActionException::class.java) {
			fillagerService.lagreFil(resource, "N8", innsendingId, FillagerNamespace.NOLOGIN)
		}
		assertEquals("Opplasting feilet. Filen er tom", exception.message)
	}

	@Test
	fun `should handle storing large file`() {
		val innsendingId = UUID.randomUUID().toString()
		val resource = loadResource("/mellomstor.pdf")

		val metadata = fillagerService.lagreFil(resource, "N10", innsendingId, FillagerNamespace.NOLOGIN)
		assertNotNull(metadata.filId)
		assertEquals(resource.byteArray.size, metadata.storrelse)
	}

	@Test
	fun `should delete all files for an innsending`() {
		val innsendingId = UUID.randomUUID().toString()
		val resource1 = loadResource("/litenPdf.pdf")
		val resource2 = loadResource("/mellomstor.pdf")

		val lagretFil1 = fillagerService.lagreFil(resource1, "N11", innsendingId, FillagerNamespace.NOLOGIN)
		val lagretFil2 = fillagerService.lagreFil(resource2, "N12", innsendingId, FillagerNamespace.NOLOGIN)

		val deleted = fillagerService.slettFiler(innsendingId, null, FillagerNamespace.NOLOGIN)
		assertTrue(deleted)

		val fil1 = fillagerService.hentFilinnhold(lagretFil1.filId, innsendingId, FillagerNamespace.NOLOGIN)
		val fil2 = fillagerService.hentFilinnhold(lagretFil2.filId, innsendingId, FillagerNamespace.NOLOGIN)

		assertNull(fil1)
		assertNull(fil2)
	}

	@Test
	fun `should update status on all files for given innsendingsid`() {
		val innsendingId = UUID.randomUUID().toString()
		val resource1 = loadResource("/litenPdf.pdf")
		val resource2 = loadResource("/mellomstor.pdf")

		val lagretFil1 = fillagerService.lagreFil(resource1, "N13", innsendingId, FillagerNamespace.NOLOGIN)
		val lagretFil2 = fillagerService.lagreFil(resource2, "N14", innsendingId, FillagerNamespace.NOLOGIN)

		fillagerService.oppdaterStatusForInnsending(innsendingId, FillagerNamespace.NOLOGIN, FilStatus.INNSENDT)

		val fil1 = fillagerService.hentFil(lagretFil1.filId, innsendingId, FillagerNamespace.NOLOGIN)
		val fil2 = fillagerService.hentFil(lagretFil2.filId, innsendingId, FillagerNamespace.NOLOGIN)

		assertNotNull(fil1)
		assertNotNull(fil2)
		assertEquals(FilStatus.INNSENDT, fil1!!.metadata.status)
		assertEquals(FilStatus.INNSENDT, fil2!!.metadata.status)
	}

	@Test
	fun `should not allow to delete a file with status INNSENDT`() {
		val innsendingId = UUID.randomUUID().toString()
		val resource = loadResource("/litenPdf.pdf")

		val metadata = fillagerService.lagreFil(resource, "N15", innsendingId, FillagerNamespace.NOLOGIN)
		fillagerService.oppdaterStatusForInnsending(innsendingId, FillagerNamespace.NOLOGIN, FilStatus.INNSENDT)

		val exception = assertThrows(no.nav.soknad.innsending.exceptions.IllegalActionException::class.java) {
			fillagerService.slettFil(metadata.filId, innsendingId, FillagerNamespace.NOLOGIN)
		}
		assertEquals("Kan ikke slette fil med status INNSENDT", exception.message)
	}

	@Test
	fun `should not allow to delete a files with status INNSENDT`() {
		val innsendingId = UUID.randomUUID().toString()
		val resource = loadResource("/litenPdf.pdf")

		fillagerService.lagreFil(resource, "N15", innsendingId, FillagerNamespace.NOLOGIN)
		fillagerService.oppdaterStatusForInnsending(innsendingId, FillagerNamespace.NOLOGIN, FilStatus.INNSENDT)

		val exception = assertThrows(no.nav.soknad.innsending.exceptions.IllegalActionException::class.java) {
			fillagerService.slettFiler(innsendingId, null, FillagerNamespace.NOLOGIN)
		}
		assertEquals("Kan ikke slette fil med status INNSENDT", exception.message)
	}

	private fun loadResource(filnavn: String): ByteArrayResource {
		return object : ByteArrayResource(Hjelpemetoder.getBytesFromFile(filnavn)) {
			override fun getFilename(): String = filnavn.removePrefix("/")
		}
	}

}

package no.nav.soknad.innsending.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.SoknadAssertions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertNotNull

class FilServiceTest : ApplicationTest() {

	@Autowired
	private lateinit var soknadService: SoknadService

	@Autowired
	private lateinit var filService: FilService

	@MockkBean
	private lateinit var subjectHandler: SubjectHandlerInterface

	@BeforeEach
	fun setup() {
		every { subjectHandler.getClientId() } returns "application"
	}

	@Test
	fun lastOppFilTilVedlegg() {
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = dokumentSoknadDto.vedleggsListe.first { "W1" == it.vedleggsnr }
		assertNotNull(vedleggDto)

		val filDtoSaved = filService.lagreFil(dokumentSoknadDto, Hjelpemetoder.lagFilDtoMedFil(vedleggDto))

		assertNotNull(filDtoSaved)
		Assertions.assertTrue(filDtoSaved.id != null)

		val hentetFilDto = filService.hentFil(dokumentSoknadDto, vedleggDto.id!!, filDtoSaved.id!!)
		Assertions.assertTrue(filDtoSaved.id == hentetFilDto.id)
	}

	@Test
	fun testValidationOfMaxFileSize() {
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = dokumentSoknadDto.vedleggsListe.first { "W1" == it.vedleggsnr }
		assertNotNull(vedleggDto)

		// Create a large file exceeding the max file size limit
		val largeFile = ByteArray(16 * 1024 * 1024) // 16 MB file
		val largeFilDto = Hjelpemetoder.lagFilDtoMedFil(Hjelpemetoder.lagVedleggDto(
			id = vedleggDto.id,
			vedleggsnr = vedleggDto.vedleggsnr!!,
			tittel = vedleggDto.tittel,
			mimeType = "application/pdf",
			fil = null
		), largeFile)

		// Expect an exception due to file size limit
		val exception = assertThrows<IllegalActionException> {
			filService.lagreFil(dokumentSoknadDto, largeFilDto)
		}

		Assertions.assertEquals(ErrorCode.VEDLEGG_FILE_SIZE_SUM_TOO_LARGE, exception.errorCode)
	}

	@Test
	fun testValidationOfMaxTotalFileSize() {
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "W2", "W3", "W4"))

		val vedleggDtoW1 = dokumentSoknadDto.vedleggsListe.first { "W1" == it.vedleggsnr }
		assertNotNull(vedleggDtoW1)

		val fileW1 = createMockFileWithSize(14)
		val largeFilDtoW1 = Hjelpemetoder.lagFilDtoMedFil(Hjelpemetoder.lagVedleggDto(
			id = vedleggDtoW1.id,
			vedleggsnr = vedleggDtoW1.vedleggsnr!!,
			tittel = vedleggDtoW1.tittel,
			mimeType = "application/pdf",
			fil = null
		), fileW1)

		filService.lagreFil(dokumentSoknadDto, largeFilDtoW1)

		val fileW2 = createMockFileWithSize(14)
		val vedleggDtoW2 = dokumentSoknadDto.vedleggsListe.first { "W2" == it.vedleggsnr }
		assertNotNull(vedleggDtoW2)
		val largeFilDtoW2 = Hjelpemetoder.lagFilDtoMedFil(Hjelpemetoder.lagVedleggDto(
			id = vedleggDtoW2.id,
			vedleggsnr = vedleggDtoW2.vedleggsnr!!,
			tittel = vedleggDtoW2.tittel,
			mimeType = "application/pdf",
			fil = null
		), fileW2)
		filService.lagreFil(dokumentSoknadDto, largeFilDtoW2)

		val fileW3 = createMockFileWithSize(14)
		val vedleggDtoW3 = dokumentSoknadDto.vedleggsListe.first { "W3" == it.vedleggsnr }
		assertNotNull(vedleggDtoW3)
		val largeFilDtoW3 = Hjelpemetoder.lagFilDtoMedFil(Hjelpemetoder.lagVedleggDto(
			id = vedleggDtoW3.id,
			vedleggsnr = vedleggDtoW3.vedleggsnr!!,
			tittel = vedleggDtoW3.tittel,
			mimeType = "application/pdf",
			fil = null
		), fileW3)
		filService.lagreFil(dokumentSoknadDto, largeFilDtoW3)

		val fileW4 = createMockFileWithSize(14)
		val vedleggDtoW4 = dokumentSoknadDto.vedleggsListe.first { "W4" == it.vedleggsnr }
		assertNotNull(vedleggDtoW4)
		val largeFilDtoW4 = Hjelpemetoder.lagFilDtoMedFil(Hjelpemetoder.lagVedleggDto(
			id = vedleggDtoW4.id,
			vedleggsnr = vedleggDtoW4.vedleggsnr!!,
			tittel = vedleggDtoW4.tittel,
			mimeType = "application/pdf",
			fil = null
		), fileW4)
		// Expect an exception due to total file size limit
		val exception = assertThrows<IllegalActionException> {
			filService.lagreFil(dokumentSoknadDto, largeFilDtoW4)
		}

		Assertions.assertEquals(ErrorCode.FILE_SIZE_SUM_TOO_LARGE, exception.errorCode)
	}

	private fun createMockFileWithSize(sizeMb: Int): ByteArray = ByteArray(sizeMb * 1024 * 1024)

	@Test
	fun slettFilTilVedlegg() {
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = dokumentSoknadDto.vedleggsListe.first { "W1" == it.vedleggsnr }
		assertNotNull(vedleggDto)

		val filDtoSaved = filService.lagreFil(dokumentSoknadDto, Hjelpemetoder.lagFilDtoMedFil(vedleggDto))

		assertNotNull(filDtoSaved)
		Assertions.assertTrue(filDtoSaved.id != null)

		val oppdatertSoknadDto = soknadService.hentSoknad(dokumentSoknadDto.id!!)
		Assertions.assertEquals(
			OpplastingsStatusDto.LastetOpp,
			oppdatertSoknadDto.vedleggsListe.first { it.id == vedleggDto.id!! }.opplastingsStatus
		)

		val oppdatertVedleggDto = filService.slettFil(oppdatertSoknadDto, filDtoSaved.vedleggsid, filDtoSaved.id!!)

		Assertions.assertEquals(OpplastingsStatusDto.IkkeValgt, oppdatertVedleggDto.opplastingsStatus)
	}

	@Test
	fun hentingAvDokumentFeilerNarIngenDokumentOpplastet() {
		// Opprett original soknad
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		// Sender inn original soknad
		assertThrows<ResourceNotFoundException> {
			filService.hentFil(dokumentSoknadDto, dokumentSoknadDto.vedleggsListe[0].id!!, 1L)
		}
	}
}

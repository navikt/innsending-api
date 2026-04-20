package no.nav.soknad.innsending.utils.validators

import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.SubmitApplicationRequest
import no.nav.soknad.innsending.util.validators.validerBrukerOgAvsender
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class SubmitApplicationRequestValidatorTest {

	@Test
	fun `skal godta gyldig bruker og avsender`() {
		val request = lagRequest(
			bruker = "12345678901",
			avsender = AvsenderDto(id = "123456789", idType = AvsenderDto.IdType.ORGNR),
		)

		assertDoesNotThrow {
			request.validerBrukerOgAvsender()
		}
	}

	@Test
	fun `skal godta avsender med kun navn naar id og idType mangler`() {
		val request = lagRequest(
			avsender = AvsenderDto(navn = "Nils Nilsen"),
		)

		assertDoesNotThrow {
			request.validerBrukerOgAvsender()
		}
	}

	@Test
	fun `skal avvise bruker med mellomrom`() {
		val exception = assertThrows<IllegalActionException> {
			lagRequest(bruker = "12345 678901").validerBrukerOgAvsender()
		}

		assertEquals("bruker kan ikke inneholde mellomrom", exception.message)
	}

	@Test
	fun `skal avvise bruker som ikke er 11 siffer`() {
		val exception = assertThrows<IllegalActionException> {
			lagRequest(bruker = "1234567890").validerBrukerOgAvsender()
		}

		assertEquals("bruker må være 11 siffer", exception.message)
	}

	@Test
	fun `skal avvise avsenderid med mellomrom`() {
		val exception = assertThrows<IllegalActionException> {
			lagRequest(avsender = AvsenderDto(id = "123 56789", idType = AvsenderDto.IdType.ORGNR)).validerBrukerOgAvsender()
		}

		assertEquals("avsender.id kan ikke inneholde mellomrom", exception.message)
	}

	@Test
	fun `skal avvise avsenderid som ikke er 9 siffer`() {
		val exception = assertThrows<IllegalActionException> {
			lagRequest(avsender = AvsenderDto(id = "12345678", idType = AvsenderDto.IdType.ORGNR)).validerBrukerOgAvsender()
		}

		assertEquals("avsender.id må være 9 siffer", exception.message)
	}

	@Test
	fun `skal avvise avsender naar id mangler men idType finnes`() {
		val exception = assertThrows<IllegalActionException> {
			lagRequest(avsender = AvsenderDto(idType = AvsenderDto.IdType.ORGNR)).validerBrukerOgAvsender()
		}

		assertEquals("avsender.id og avsender.idType må begge oppgis hvis en av dem er satt", exception.message)
	}

	@Test
	fun `skal avvise avsender naar idType mangler men id finnes`() {
		val exception = assertThrows<IllegalActionException> {
			lagRequest(avsender = AvsenderDto(id = "123456789")).validerBrukerOgAvsender()
		}

		assertEquals("avsender.id og avsender.idType må begge oppgis hvis en av dem er satt", exception.message)
	}

	@Test
	fun `skal avvise avsender uten navn naar id og idType mangler`() {
		val exception = assertThrows<IllegalActionException> {
			lagRequest(avsender = AvsenderDto()).validerBrukerOgAvsender()
		}

		assertEquals("avsender.navn må oppgis når avsender.id og avsender.idType ikke er satt", exception.message)
	}

	private fun lagRequest(bruker: String? = null, avsender: AvsenderDto? = null) = SubmitApplicationRequest(
		formNumber = "NAV 11-12.12",
		title = "Testskjema",
		tema = "BIL",
		language = "nb",
		mainDocument = byteArrayOf(1),
		mainDocumentAlt = byteArrayOf(2),
		bruker = bruker,
		avsender = avsender,
	)
}

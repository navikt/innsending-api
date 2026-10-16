package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SubmitApplicationRequest
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.VedleggDtoTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class SoknadMappingTest {
	@Test
	fun `forwards grant user digital access only when true`() {
		val soknad = DokumentSoknadDtoTestBuilder(status = SoknadsStatusDto.Opprettet).build()
		val hoveddokument = VedleggDtoTestBuilder().asHovedDokument().build()
		val avsender = AvsenderDto(id = "12345678901", idType = AvsenderDto.IdType.FNR)
		val bruker = BrukerDto(id = "12345678901", idType = BrukerDto.IdType.FNR)

		assertEquals(true, translate(soknad, listOf(hoveddokument), avsender, bruker, true).grantUserDigitalAccess)
		assertEquals(null, translate(soknad, listOf(hoveddokument), avsender, bruker, false).grantUserDigitalAccess)
		assertEquals(null, translate(soknad, listOf(hoveddokument), avsender, bruker, null).grantUserDigitalAccess)
	}

	@Test
	fun `stores grant user digital access from submit application request`() {
		val request = SubmitApplicationRequest(
			formNumber = "NAV 00-00.00",
			title = "Test application",
			tema = "BIL",
			language = "nb",
			mainDocument = byteArrayOf(),
			mainDocumentAlt = byteArrayOf(),
			grantUserDigitalAccess = true,
		)

		assertEquals(true, request.toDokumentSoknadDto(java.util.UUID.randomUUID(), "test").grantuserdigitalaccess)
	}
}

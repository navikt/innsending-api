package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SubmitApplicationRequest
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.VedleggDtoTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class SoknadMappingTest {
	@Test
	fun `maps a verified digital owner without changing the legacy user`() {
		val application = DokumentSoknadDtoTestBuilder(brukerId = "12345678901", id = 1).build()
		val owner = ApplicationOwner.person("10987654321")

		val storedApplication = mapTilSoknadDb(application, java.util.UUID.randomUUID().toString(), owner = owner)

		assertEquals(owner, storedApplication.owner)
		assertEquals(application.brukerId, storedApplication.brukerid)
		assertEquals(application.brukerId, lagDokumentSoknadDto(storedApplication, emptyList()).brukerId)
	}

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
			bruker = "12345678901",
		)

		val storedApplication = request.toDokumentSoknadDto(java.util.UUID.randomUUID(), "test")
		assertEquals(true, storedApplication.grantuserdigitalaccess)
		assertEquals(null, storedApplication.owner)
		assertEquals(request.bruker, storedApplication.brukerid)
	}

	@Test
	fun `keeps nologin owner absent when the request has no user`() {
		val request = SubmitApplicationRequest(
			formNumber = "NAV 00-00.00",
			title = "Test application",
			tema = "BIL",
			language = "nb",
			mainDocument = byteArrayOf(),
			mainDocumentAlt = byteArrayOf(),
		)

		val storedApplication = request.toDokumentSoknadDto(java.util.UUID.randomUUID(), "test")
		assertEquals(null, storedApplication.owner)
		assertEquals(null, storedApplication.brukerid)
	}
}

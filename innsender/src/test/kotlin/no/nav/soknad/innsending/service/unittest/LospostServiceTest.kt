package no.nav.soknad.innsending.service.unittest

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.LospostService
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.util.Constants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class LospostServiceTest {

	private val defaultUser = "12345678901"

	@RelaxedMockK
	lateinit var repo: RepositoryUtils

	@RelaxedMockK
	lateinit var subjectHandler: SubjectHandlerInterface

	@InjectMockKs
	lateinit var lospostService: LospostService

	@Test
	fun `Should create one soknad and one vedlegg`() {
		every { repo.lagreSoknad(any()) } answers {
			val dto = firstArg<SoknadDbData>().copy(id = 1)
			dto
		}
		every { repo.lagreVedlegg(any()) } answers {
			val dto = firstArg<VedleggDbData>().copy(id = 2)
			dto
		}

		val lospost = lospostService.saveLospostInnsending(
			defaultUser,
			"PEN",
			"Send dokumentasjon til Nav",
			"Pensjonsbevis",
			"nb"
		)

		assertEquals(1, lospost.vedleggsListe?.size)

		val captorSoknad = slot<SoknadDbData>()
		val captorVedlegg = slot<VedleggDbData>()

		verify(exactly = 1) { repo.lagreSoknad(capture(captorSoknad)) }
		verify(exactly = 1) { repo.lagreVedlegg(capture(captorVedlegg)) }

		val soknad = captorSoknad.captured
		assertEquals(VisningsType.lospost, soknad.visningstype)
		assertEquals(Constants.LOSPOST_SKJEMANUMMER, soknad.skjemanr)
		assertEquals("Send dokumentasjon til Nav", soknad.tittel)

		val vedlegg = captorVedlegg.captured
		assertEquals("N6", vedlegg.vedleggsnr)
		assertEquals("Pensjonsbevis", vedlegg.tittel)
	}
}

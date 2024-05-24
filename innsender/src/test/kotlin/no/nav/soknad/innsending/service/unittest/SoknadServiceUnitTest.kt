package no.nav.soknad.innsending.service.unittest

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.*
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class SoknadServiceUnitTest {

	private val defaultUser = "12345678901"

	@RelaxedMockK
	lateinit var skjemaService: SkjemaService

	@RelaxedMockK
	lateinit var vedleggService: VedleggService

	@RelaxedMockK
	lateinit var ettersendingService: EttersendingService

	@RelaxedMockK
	lateinit var filService: FilService

	@RelaxedMockK
	lateinit var brukernotifikasjonPublisher: BrukernotifikasjonPublisher

	@RelaxedMockK
	lateinit var innsenderMetrics: InnsenderMetrics

	@RelaxedMockK
	lateinit var exceptionHelper: ExceptionHelper

	@RelaxedMockK
	lateinit var repo: RepositoryUtils

	@RelaxedMockK
	lateinit var subjectHandler: SubjectHandlerInterface

	@InjectMockKs
	lateinit var soknadService: SoknadService

	@Test
	fun `Should return correct active soknader`() {
		// Given 1 søknad from fyllUt, 0 ettersending and 1 dokumentInnsending for defaultUser
		val soknadDb = SoknadDbDataTestBuilder(brukerId = defaultUser).build()
		val dokumentInnsendingSoknad =
			SoknadDbDataTestBuilder(brukerId = defaultUser, visningsType = VisningsType.dokumentinnsending).build()
		val skjemanr = soknadDb.skjemanr
		val dokumentSoknadDto = DokumentSoknadDtoTestBuilder(skjemanr = skjemanr, brukerId = defaultUser).build()
		val dokumentInnsendingSoknadDto = DokumentSoknadDtoTestBuilder(
			skjemanr = skjemanr,
			brukerId = defaultUser,
			visningsType = VisningsType.dokumentinnsending
		).build()

		every { repo.finnAlleSoknaderGittBrukerIdOgStatus(defaultUser, SoknadsStatus.Opprettet) } returns listOf(
			soknadDb,
			dokumentInnsendingSoknad
		)
		every { vedleggService.hentAlleVedlegg(soknadDb) } returns dokumentSoknadDto
		every { vedleggService.hentAlleVedlegg(dokumentInnsendingSoknad) } returns dokumentInnsendingSoknadDto

		// When
		val soknader = soknadService.hentAktiveSoknader(defaultUser, skjemanr, SoknadType.soknad)
		val ettersendinger = soknadService.hentAktiveSoknader(defaultUser, skjemanr, SoknadType.ettersendelse)

		// Then
		// hentAktiveSoknader is called 2 times, one for each type
		verify(exactly = 2) { repo.finnAlleSoknaderGittBrukerIdOgStatus(defaultUser, SoknadsStatus.Opprettet) }
		verify(exactly = 2) { vedleggService.hentAlleVedlegg(soknadDb) }

		assertEquals(1, soknader.size)
		assertEquals(skjemanr, soknader[0].skjemanr)
		assertEquals(SoknadType.soknad, soknader[0].soknadstype)

		assertEquals(0, ettersendinger.size)
	}
}

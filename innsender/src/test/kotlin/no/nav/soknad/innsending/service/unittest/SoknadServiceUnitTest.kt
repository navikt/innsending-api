package no.nav.soknad.innsending.service.unittest

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.service.*
import no.nav.soknad.innsending.service.fillager.FileStorage
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.util.mapping.mapTilSoknadDb
import no.nav.soknad.innsending.util.stringextensions.toUUID
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

@ExtendWith(MockKExtension::class)
class SoknadServiceUnitTest {

	private val defaultUser = "12345678901"

	@RelaxedMockK
	lateinit var vedleggService: VedleggService

	@RelaxedMockK
	lateinit var ettersendingService: EttersendingService

	@RelaxedMockK
	lateinit var filService: FilService

	@RelaxedMockK
	lateinit var fileStorage: FileStorage

	@RelaxedMockK
	lateinit var brukernotifikasjonPublisher: BrukernotifikasjonPublisher

	@RelaxedMockK
	lateinit var innsenderMetrics: InnsenderMetrics

	@RelaxedMockK
	lateinit var exceptionHelper: ExceptionHelper

	@RelaxedMockK
	lateinit var repo: RepositoryUtils

	@InjectMockKs
	lateinit var soknadService: SoknadService

	@ParameterizedTest(name = "owner present: {0}")
	@ValueSource(booleans = [true, false])
	fun `updates preserve the stored owner`(ownerPresent: Boolean) {
		val application = DokumentSoknadDtoTestBuilder(brukerId = defaultUser, id = 1, vedleggsListe = emptyList()).build()
		val applicationId = application.innsendingsId!!
		val owner = if (ownerPresent) ApplicationOwner.person("10987654321") else null
		val storedApplication = mapTilSoknadDb(application, applicationId, owner = owner).copy(
			avsender = AvsenderDto(id = defaultUser, idType = AvsenderDto.IdType.FNR),
			affecteduser = BrukerDto(id = defaultUser, idType = BrukerDto.IdType.FNR),
			grantuserdigitalaccess = true,
		)
		val savedApplication = slot<SoknadDbData>()
		every { repo.hentSoknadDb(applicationId) } returns storedApplication
		every { vedleggService.hentAlleVedlegg(storedApplication, applicationId) } returns application
		every { repo.lagreSoknad(capture(savedApplication)) } answers { firstArg() }

		soknadService.updateSoknad(storedApplication, application, application.copy(tittel = "Updated title"), SoknadsStatus.Utfylt)

		assertEquals(storedApplication.owner, savedApplication.captured.owner)
		assertEquals(storedApplication.brukerid, savedApplication.captured.brukerid)
		assertEquals(SoknadsStatus.Utfylt, savedApplication.captured.status)
		assertEquals("Updated title", savedApplication.captured.tittel)
		assertNull(savedApplication.captured.avsender)
		assertNull(savedApplication.captured.affecteduser)
		assertNull(savedApplication.captured.grantuserdigitalaccess)
	}

	@ParameterizedTest(name = "owner present: {0}")
	@ValueSource(booleans = [true, false])
	fun `automatic deletion preserves the stored owner`(ownerPresent: Boolean) {
		val application = DokumentSoknadDtoTestBuilder(brukerId = defaultUser, id = 1, vedleggsListe = emptyList()).build()
		val applicationId = application.innsendingsId!!
		val owner = if (ownerPresent) ApplicationOwner.person("10987654321") else null
		val storedApplication = mapTilSoknadDb(application, applicationId, owner = owner).copy(
			avsender = AvsenderDto(id = defaultUser, idType = AvsenderDto.IdType.FNR),
			affecteduser = BrukerDto(id = defaultUser, idType = BrukerDto.IdType.FNR),
			grantuserdigitalaccess = true,
		)
		val savedApplication = slot<SoknadDbData>()
		every { repo.hentSoknadDb(applicationId) } returns storedApplication
		every { vedleggService.hentAlleVedlegg(storedApplication, applicationId) } returns application
		every { repo.lagreSoknad(capture(savedApplication)) } answers { firstArg() }

		soknadService.slettSoknadAutomatisk(applicationId)

		assertEquals(storedApplication.owner, savedApplication.captured.owner)
		assertEquals(storedApplication.brukerid, savedApplication.captured.brukerid)
		assertEquals(SoknadsStatus.AutomatiskSlettet, savedApplication.captured.status)
		assertEquals(application.tittel, savedApplication.captured.tittel)
		assertNull(savedApplication.captured.avsender)
		assertNull(savedApplication.captured.affecteduser)
		assertNull(savedApplication.captured.grantuserdigitalaccess)
	}

	@Test
	fun `storage deletion failure leaves application data intact for retry`() {
		val application = DokumentSoknadDtoTestBuilder(visningsType = VisningsType.fyllUt).build()
		val innsendingsId = application.innsendingsId!!
		val applicationRow = SoknadDbDataTestBuilder(
			id = application.id,
			innsendingsId = innsendingsId,
			visningsType = VisningsType.fyllUt,
		).build()
		val storageFailure = RuntimeException("storage unavailable")
		every { repo.hentSoknadDbForUpdate(innsendingsId) } returns applicationRow
		every { vedleggService.hentAlleVedlegg(applicationRow, innsendingsId) } returns application
		every {
			fileStorage.delete(
				FileStorageNamespace.DIGITAL,
				innsendingsId.toUUID(),
				null,
				null,
				false,
			)
		} throws storageFailure

		val failure = assertFailsWith<RuntimeException> {
			soknadService.slettSoknadAvBruker(application)
		}
		assertSame(storageFailure, failure)

		verify(exactly = 1) {
			fileStorage.delete(FileStorageNamespace.DIGITAL, innsendingsId.toUUID(), null, null, false)
		}
		verify(exactly = 0) { vedleggService.slettVedleggOgDensFiler(any()) }
		verify(exactly = 0) { repo.slettSoknad(any(), any()) }
	}

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
		val alleTyper = soknadService.hentAktiveSoknader(defaultUser, skjemanr, *emptyArray())

		// Then
		// hentAktiveSoknader is called 3 times
		verify(exactly = 3) { repo.finnAlleSoknaderGittBrukerIdOgStatus(defaultUser, SoknadsStatus.Opprettet) }
		verify(exactly = 3) { vedleggService.hentAlleVedlegg(soknadDb) }

		assertEquals(1, soknader.size)
		assertEquals(skjemanr, soknader[0].skjemanr)
		assertEquals(SoknadType.soknad, soknader[0].soknadstype)

		assertEquals(0, ettersendinger.size)

		assertEquals(1, alleTyper.size)
		assertEquals(skjemanr, alleTyper[0].skjemanr)
		assertEquals(SoknadType.soknad, alleTyper[0].soknadstype)
	}
}

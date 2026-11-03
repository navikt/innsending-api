package no.nav.soknad.innsending.service.unittest

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InnsendingPartyRetryServiceUnitTest {

	@Test
	fun `archive retry after outgoing failure keeps an unidentified party instead of falling back to owner`() {
		val sender = AvsenderDto(id = "123456789", idType = AvsenderDto.IdType.ORGNR, navn = "Synthetic organization")
		val original = SoknadDbDataTestBuilder(status = SoknadsStatus.KlarForInnsending).build().copy(
			affecteduser = null,
			avsender = sender,
			grantuserdigitalaccess = false,
			// Distinct non-null identities make both owner and legacy-user fallbacks observable defects.
			owner = ApplicationOwner.person("10987654321"),
		)
		val applicationId = original.innsendingsid
		val application = DokumentSoknadDtoTestBuilder(
			innsendingsId = applicationId,
			brukerId = checkNotNull(original.brukerid),
			status = SoknadsStatusDto.KlarForInnsending,
			vedleggsListe = emptyList(),
		).build()
		val repository = mockk<RepositoryUtils>()
		val applications = mockk<SoknadService>()
		val receiver = mockk<MottakerInterface>()
		val service = InnsendingService(
			repo = repository,
			soknadService = applications,
			filService = mockk(),
			vedleggService = mockk(),
			tilleggstonadService = mockk(),
			soknadsmottakerAPI = receiver,
			restConfig = mockk(),
			exceptionHelper = mockk(relaxed = true),
			ettersendingService = mockk(),
			innsenderMetrics = mockk(relaxed = true),
			pdlInterface = mockk(),
			documentService = mockk(),
		)
		every { repository.hentSoknadDb(applicationId) } returns original
		every { applications.hentSoknad(applicationId) } returns application
		val users = mutableListOf<BrukerDto?>()
		var failFirstAttempt = true
		every { receiver.sendInnSoknad(any(), any(), any(), any(), any()) } answers {
			users.add(arg(3))
			if (failFirstAttempt) {
				failFirstAttempt = false
				throw IllegalStateException("Synthetic receiver failure")
			}
		}
		val completed = slot<SoknadDbData>()
		every { repository.updateStatusAvsenderAndAffecteduser(capture(completed)) } answers { firstArg() }

		assertThrows<BackendErrorException> { service.sendInnForArkivering(applicationId) }

		assertEquals(1, users.size)
		assertNull(users.single(), "The failed attempt must not substitute the owner or legacy user")
		verify(exactly = 0) { repository.updateStatusAvsenderAndAffecteduser(any()) }

		// Retry starts from the same pending stored state, without a new submission request.
		service.sendInnForArkivering(applicationId)

		assertEquals(2, users.size)
		assertNull(users.last(), "Retry must retain the absence of an affected identifier")
		verify(exactly = 2) {
			receiver.sendInnSoknad(match { it.innsendingsId == applicationId }, eq(emptyList()), eq(sender), isNull(), eq(false))
		}
		verify(exactly = 1) { repository.updateStatusAvsenderAndAffecteduser(any()) }
		assertEquals(SoknadsStatus.Innsendt, completed.captured.status)
		assertNull(completed.captured.affecteduser)
		assertTrue(completed.captured.avsender == sender)
		assertTrue(completed.captured.owner == original.owner)
		assertTrue(completed.captured.brukerid == original.brukerid)
		assertEquals(false, completed.captured.grantuserdigitalaccess)
	}
}

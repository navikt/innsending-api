package no.nav.soknad.innsending.service.unittest

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.BrukerNotifikasjonConfig
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.location.UrlHandler
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.NotificationService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.Thread.sleep
import kotlin.test.assertEquals


@ExtendWith(MockKExtension::class)
class NotificationServiceUnitTest {

	private val defaultUser = "12345678901"

	@RelaxedMockK
	lateinit var innsenderMetrics: InnsenderMetrics

	@RelaxedMockK
	lateinit var exceptionHelper: ExceptionHelper

	@RelaxedMockK
	lateinit var subjectHandler: SubjectHandlerInterface

	@MockK
	lateinit var notifikasjonConfig: BrukerNotifikasjonConfig

	@MockK
	lateinit var kafkaPublisher: PublisherInterface

	@MockK
	lateinit var urlHandler: UrlHandler

	@RelaxedMockK
	lateinit var soknadRepository: SoknadRepository

	@InjectMockKs
	lateinit var brukernotifikasjonPublisher: BrukernotifikasjonPublisher

	@InjectMockKs
	lateinit var notificationService: NotificationService

	@Test
	fun `Should publish new notification when requested`() {
		// Given 1 søknad with defaultUser in state Opprettet
		val soknadDb = SoknadDbDataTestBuilder(brukerId = defaultUser, skjemanr="NAV 20-74.13", status = SoknadsStatus.Opprettet).build()
		every {soknadRepository.findByInnsendingsid(soknadDb.innsendingsid)} returns soknadDb
		every {kafkaPublisher.opprettBrukernotifikasjon(any())} returns Unit
		every {urlHandler.getFyllutUrl(any())} returns "https://fyllut.intern.nav.no/fyllut/${soknadDb.innsendingsid}"
		every {urlHandler.getSendInnUrl(any())} returns "https://sendinn.intern.nav.no/${soknadDb.innsendingsid}"
		every {notifikasjonConfig.publisereEndringer} returns true

		val captorNotification = slot<AddNotification>()

		// When
		notificationService.create(soknadDb.innsendingsid)

		// Then
		sleep(20)
		verify(exactly = 2) { soknadRepository.findByInnsendingsid(soknadDb.innsendingsid) }
		verify(exactly = 1) { kafkaPublisher.opprettBrukernotifikasjon(capture(captorNotification)) }

		val expectedLink = "https://fyllut.intern.nav.no/fyllut/${soknadDb.innsendingsid}/nav207413/oppsummering?sub=digital&innsendingsId=${soknadDb.innsendingsid}"
		val addNotification = captorNotification.captured
		assertEquals(soknadDb.innsendingsid, addNotification.soknadRef.innsendingId)
		assertEquals(soknadDb.brukerid, addNotification.soknadRef.personId)
		assertEquals(expectedLink, addNotification.brukernotifikasjonInfo.lenke)
		assertEquals(soknadDb.tittel, addNotification.brukernotifikasjonInfo.notifikasjonsTittel)
	}


	@Test
	fun `Should not publish new notification when status innsendt`() {
		// Given 1 søknad with defaultUser in state Opprettet
		val soknadDb = SoknadDbDataTestBuilder(brukerId = defaultUser, skjemanr="NAV 20-74.13", status = SoknadsStatus.Innsendt).build()
		every {soknadRepository.findByInnsendingsid(soknadDb.innsendingsid)} returns soknadDb
		every {kafkaPublisher.opprettBrukernotifikasjon(any())} returns Unit
		every {urlHandler.getFyllutUrl(any())} returns "https://fyllut.intern.nav.no/fyllut/${soknadDb.innsendingsid}"
		every {urlHandler.getSendInnUrl(any())} returns "https://sendinn.intern.nav.no/${soknadDb.innsendingsid}"
		every {notifikasjonConfig.publisereEndringer} returns true

		// When
		notificationService.create(soknadDb.innsendingsid)

		// Then
		sleep(40)
		verify(exactly = 2) { soknadRepository.findByInnsendingsid(soknadDb.innsendingsid) }
		verify(exactly = 0) { kafkaPublisher.opprettBrukernotifikasjon(any()) }

	}


	@Test
	fun `Should not publish new notification when brukerid is null`() {
		// Given 1 søknad with defaultUser in state Opprettet
		val soknadDb = SoknadDbDataTestBuilder(brukerId = null, skjemanr="NAV 20-74.13", status = SoknadsStatus.Opprettet).build()
		every {soknadRepository.findByInnsendingsid(soknadDb.innsendingsid)} returns soknadDb
		every {kafkaPublisher.opprettBrukernotifikasjon(any())} throws RuntimeException("An error occured")
		every {urlHandler.getFyllutUrl(any())} returns "https://fyllut.intern.nav.no/fyllut/${soknadDb.innsendingsid}"
		every {urlHandler.getSendInnUrl(any())} returns "https://sendinn.intern.nav.no/${soknadDb.innsendingsid}"
		every {notifikasjonConfig.publisereEndringer} returns true

		// When
		notificationService.create(soknadDb.innsendingsid)

		// Then
		sleep(20)	//	Liten delay for å sikre at asynkrone operasjoner er fullført før verifisering
		verify(exactly = 2) { soknadRepository.findByInnsendingsid(soknadDb.innsendingsid) }
		verify(exactly = 0) { kafkaPublisher.opprettBrukernotifikasjon(any()) }

	}


	@Test
	fun `Should publish close notification`() {
		// Given 1 søknad with defaultUser in state Opprettet
		val soknadDb = SoknadDbDataTestBuilder(brukerId = defaultUser, skjemanr="NAV 20-74.13", status = SoknadsStatus.Opprettet).build()
		every {soknadRepository.findByInnsendingsid(soknadDb.innsendingsid)} returns soknadDb
		every {kafkaPublisher.avsluttBrukernotifikasjon(any())} returns Unit
		every {urlHandler.getFyllutUrl(any())} returns "https://fyllut.intern.nav.no/fyllut/${soknadDb.innsendingsid}"
		every {urlHandler.getSendInnUrl(any())} returns "https://sendinn.intern.nav.no/${soknadDb.innsendingsid}"
		every {notifikasjonConfig.publisereEndringer} returns true

		val captorSoknadRef = slot<SoknadRef>()

		// When
		notificationService.close(soknadDb.innsendingsid)

		// Then
		verify(exactly = 1) { soknadRepository.findByInnsendingsid(soknadDb.innsendingsid) }
		verify(exactly = 1) { kafkaPublisher.avsluttBrukernotifikasjon(capture(captorSoknadRef)) }

		val soknadRef = captorSoknadRef.captured
		assertEquals(soknadDb.innsendingsid, soknadRef.innsendingId)
		assertEquals(soknadDb.brukerid, soknadRef.personId)
	}

	@Test
	fun `Should not publish close notification if brukerid is null`() {
		// Given 1 søknad with defaultUser in state Opprettet
		val soknadDb = SoknadDbDataTestBuilder(brukerId = null, skjemanr="NAV 20-74.13", status = SoknadsStatus.Opprettet).build()
		every {soknadRepository.findByInnsendingsid(soknadDb.innsendingsid)} returns soknadDb
		every {kafkaPublisher.avsluttBrukernotifikasjon(any())} returns Unit
		every {urlHandler.getFyllutUrl(any())} returns "https://fyllut.intern.nav.no/fyllut/${soknadDb.innsendingsid}"
		every {urlHandler.getSendInnUrl(any())} returns "https://sendinn.intern.nav.no/${soknadDb.innsendingsid}"
		every {notifikasjonConfig.publisereEndringer} returns true

		// When
		notificationService.close(soknadDb.innsendingsid)

		// Then
		sleep(20)
		verify(exactly = 1) { soknadRepository.findByInnsendingsid(soknadDb.innsendingsid) }
		verify(exactly = 0) { kafkaPublisher.avsluttBrukernotifikasjon(any()) }

	}


}

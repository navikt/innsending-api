package no.nav.soknad.innsending.brukernotifikasjon

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.config.BrukerNotifikasjonConfig
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertFalse

class OwnerNotificationUnitTest {
	private val publisher = mockk<PublisherInterface>()
	private val repository = mockk<SoknadRepository>()
	private val config = mockk<BrukerNotifikasjonConfig> { every { publisereEndringer } returns true }
	private val notifications = BrukernotifikasjonPublisher(config, publisher, mockk(), repository)

	@ParameterizedTest
	@MethodSource("ineligibleOwners")
	fun `ineligible owner never receives a new notification`(type: VisningsType?, owner: ApplicationOwner?) {
		val application = SoknadDbDataTestBuilder().build().copy(visningstype = type, owner = owner)
		every { repository.findByInnsendingsid(application.innsendingsid) } returns application
		assertFalse(notifications.createNotification(application))
		verify(exactly = 0) { publisher.opprettBrukernotifikasjon(any()) }
	}

	@ParameterizedTest
	@MethodSource("ineligibleOwners")
	fun `ineligible owner never receives a closing notification`(type: VisningsType?, owner: ApplicationOwner?) {
		val application = SoknadDbDataTestBuilder().build().copy(visningstype = type, owner = owner)
		assertFalse(notifications.closeNotification(application))
		verify(exactly = 0) { publisher.avsluttBrukernotifikasjon(any()) }
	}

	companion object {
		@JvmStatic
		fun ineligibleOwners() = listOf(
			Arguments.of(null, ApplicationOwner.person("12345678901")),
			Arguments.of(VisningsType.fyllUt, null),
			Arguments.of(VisningsType.fyllUt, ApplicationOwner(null, null)),
			Arguments.of(VisningsType.fyllUt, ApplicationOwner("12345678901", "ORGNR")),
			Arguments.of(VisningsType.fyllUt, ApplicationOwner.person("invalid")),
			Arguments.of(VisningsType.nologin, ApplicationOwner.person("12345678901")),
		)
	}
}

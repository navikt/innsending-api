package no.nav.soknad.innsending.cleanup

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.*
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import kotlin.test.assertEquals

class FjernGamleSoknaderTest : ApplicationTest() {

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	private lateinit var notificationService: NotificationService

	@Autowired
	private lateinit var soknadService: SoknadService

	private val leaderSelectionUtility = mockk<LeaderSelectionUtility>()

	private val soknadsmottakerAPI = mockk<MottakerInterface>()

	@MockkBean
	private lateinit var subjectHandler: SubjectHandlerInterface

	@SpykBean
	lateinit var notificationPublisher: PublisherInterface

	private val defaultSkjemanr = "NAV 55-00.60"

	@Test
	fun testSlettingAvGammelIkkeInnsendtSoknad() {
		every { leaderSelectionUtility.isLeader() } returns true
		every { soknadsmottakerAPI.sendInnSoknad(any(), any(), any(), any()) } returns Unit
		every { subjectHandler.getClientId() } returns "application"

		val spraak = "no"
		val tema = "BID"

		val gammelSoknadId = soknadService.opprettNySoknad(
			Hjelpemetoder.lagDokumentSoknad(
				brukerId = "12345678901", skjemanr = defaultSkjemanr, spraak = spraak, tittel = "En test",
				tema = tema, id = null, innsendingsid = null, soknadsStatus = SoknadsStatusDto.Opprettet, vedleggsListe = null,
				ettersendingsId = null, OffsetDateTime.now().minusDays(DEFAULT_LEVETID_OPPRETTET_SOKNAD + 1)
			)
		).innsendingsId!!

		val nyereSoknadId = soknadService.opprettNySoknad(
			Hjelpemetoder.lagDokumentSoknad(
				brukerId = "12345678901", skjemanr = defaultSkjemanr, spraak = spraak, tittel = "En test",
				tema = tema, id = null, innsendingsid = null, soknadsStatus = SoknadsStatusDto.Opprettet, vedleggsListe = null,
				ettersendingsId = null, OffsetDateTime.now().minusDays(DEFAULT_LEVETID_OPPRETTET_SOKNAD - 1)
			)
		).innsendingsId!!

		val initAntall = innsenderMetrics.getOperationsCounter(InnsenderOperation.SLETT.name, tema)
		val fjernGamleSoknader = FjernGamleSoknader(soknadService, notificationService, leaderSelectionUtility)

		fjernGamleSoknader.fjernGamleIkkeInnsendteSoknader()

		val notificationsClosed = mutableListOf<SoknadRef>()
		verify(exactly = 1) { notificationPublisher.avsluttBrukernotifikasjon(capture(notificationsClosed)) }
		assertEquals(1, notificationsClosed.size)

		val slettetSoknad = soknadService.hentSoknad(gammelSoknadId)
		assertTrue(slettetSoknad.status == SoknadsStatusDto.AutomatiskSlettet)
		assertTrue(notificationsClosed.any { it.innsendingId == gammelSoknadId })
		assertEquals(1.0 + (initAntall ?: 0.0), innsenderMetrics.getOperationsCounter(InnsenderOperation.SLETT.name, tema))

		val beholdtSoknad = soknadService.hentSoknad(nyereSoknadId)
		assertTrue(beholdtSoknad.status == SoknadsStatusDto.Opprettet)
		assertTrue(notificationsClosed.none { it.innsendingId == nyereSoknadId })
	}

}

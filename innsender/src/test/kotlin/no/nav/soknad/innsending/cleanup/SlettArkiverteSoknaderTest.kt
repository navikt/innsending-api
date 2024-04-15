package no.nav.soknad.innsending.cleanup

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.pdl.PdlAPI
import no.nav.soknad.innsending.consumerapis.pdl.dto.IdentDto
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.FilService
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import kotlin.test.assertNotNull

class SlettArkiverteSoknaderTest : ApplicationTest() {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	private lateinit var filService: FilService

	@Autowired
	private lateinit var soknadService: SoknadService

	@Autowired
	private lateinit var innsendingService: InnsendingService

	@MockkBean
	private lateinit var brukernotifikasjonPublisher: BrukernotifikasjonPublisher

	@MockkBean
	private lateinit var leaderSelectionUtility: LeaderSelectionUtility

	@MockkBean
	private lateinit var soknadsmottakerAPI: MottakerAPITest

	@MockkBean
	private lateinit var pdlInterface: PdlAPI

	@MockkBean
	private lateinit var subjectHandler: SubjectHandlerInterface

	private val defaultSkjemanr = "NAV 55-00.60"

	@Test
	fun testSlettingAvInnsendteSoknader() {
		SlettArkiverteSoknader(leaderSelectionUtility, soknadService)

		val soknader = mutableListOf<DokumentSoknadDto>()
		every { brukernotifikasjonPublisher.soknadStatusChange(capture(soknader)) } returns true
		every { leaderSelectionUtility.isLeader() } returns true
		every { soknadsmottakerAPI.sendInnSoknad(any(), any()) } returns Unit
		every { pdlInterface.hentPersonIdents(any()) } returns listOf(IdentDto("123456789", "FOLKEREGISTERIDENT", false))
		every { pdlInterface.hentPersonData(any()) } returns PersonDto("123456789", "Fornavn", null, "Etternavn")
		every { subjectHandler.getClientId() } returns "application"

		val spraak = "no"
		val tema = "BID"

		val skalSendeInnOgArkivereId = soknadService.opprettNySoknad(
			Hjelpemetoder.lagDokumentSoknad(
				brukerId = "12345678901", skjemanr = defaultSkjemanr, spraak = spraak, tittel = "En test",
				tema = tema, id = null, innsendingsid = null, soknadsStatus = SoknadsStatusDto.Opprettet, vedleggsListe = null,
				ettersendingsId = null, OffsetDateTime.now().minusDays(1)
			)
		).innsendingsId!!

		val skalSendeInnIkkeArkivereId = soknadService.opprettNySoknad(
			Hjelpemetoder.lagDokumentSoknad(
				brukerId = "12345678901", skjemanr = defaultSkjemanr, spraak = spraak, tittel = "En test",
				tema = tema, id = null, innsendingsid = null, soknadsStatus = SoknadsStatusDto.Opprettet, vedleggsListe = null,
				ettersendingsId = null, OffsetDateTime.now().minusDays(1)
			)
		).innsendingsId!!

		val initAntall = innsenderMetrics.getOperationsCounter(InnsenderOperation.SLETT.name, tema) ?: 0.0
		sendInnSoknad(soknadService, skalSendeInnOgArkivereId, innsendingService)
		sendInnSoknad(soknadService, skalSendeInnIkkeArkivereId, innsendingService)

		// Simuler mottatt melding fra soknadsarkiverer og sett arkiveringsstatus=arkivert eller arkiveringFeilet
		simulerArkiveringsRespons(skalSendeInnOgArkivereId, ArkiveringsStatus.Arkivert)
		simulerArkiveringsRespons(skalSendeInnIkkeArkivereId, ArkiveringsStatus.ArkiveringFeilet)

		// Hvis kall for å slette innsendte og arkivertesøknader kalles
		soknadService.finnOgSlettArkiverteSoknader(-1, 100)

		// Så skal innsendt og arkivert søknad være slettet
		assertThrows<ResourceNotFoundException> {
			soknadService.hentSoknad(skalSendeInnOgArkivereId)
		}

		// Og innsendt og ikke arkivert Soknad skal ikke være slettet.
		val beholdtSoknad = soknadService.hentSoknad(skalSendeInnIkkeArkivereId)
		Assertions.assertTrue(beholdtSoknad.status == SoknadsStatusDto.Innsendt)

		// Og metrics for antall slettede søknader er økt med 1
		Assertions.assertEquals(
			initAntall + 1.0,
			innsenderMetrics.getOperationsCounter(InnsenderOperation.SLETT.name, tema)
		)

	}

	private fun sendInnSoknad(
		soknadService: SoknadService,
		soknadsId: String,
		innsendingService: InnsendingService
	) {
		// HentSoknad
		val skalSendeInnSoknad = soknadService.hentSoknad(soknadsId)
		//Lagre søknad pdf
		filService.lagreFil(
			skalSendeInnSoknad,
			Hjelpemetoder.lagFilDtoMedFil(skalSendeInnSoknad.vedleggsListe.first { it.erHoveddokument && !it.erVariant })
		)
		//Send inn søknad
		val kvittering = innsendingService.sendInnSoknad(skalSendeInnSoknad)
		assertNotNull(kvittering)
	}

	private fun simulerArkiveringsRespons(innsendingsId: String, arkiveringsStatus: ArkiveringsStatus) {
		val soknad = repo.hentSoknadDb(innsendingsId)
		repo.oppdaterArkiveringsstatus(soknad, arkiveringsStatus)
	}

}

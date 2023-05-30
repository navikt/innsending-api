package no.nav.soknad.innsending.cleanup

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.IdentDto
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.repository.ArkiveringsStatus
import no.nav.soknad.innsending.service.*
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import kotlin.test.assertTrue

class SlettArkiverteSoknaderTest : ApplicationTest() {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var skjemaService: SkjemaService

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	private lateinit var vedleggService: VedleggService

	@Autowired
	private lateinit var ettersendingService: EttersendingService

	@Autowired
	private lateinit var filService: FilService

	@Autowired
	private lateinit var restConfig: RestConfig

	@Autowired
	private lateinit var exceptionHelper: ExceptionHelper

	@InjectMockKs
	private val brukernotifikasjonPublisher = mockk<BrukernotifikasjonPublisher>()

	@InjectMockKs
	private val leaderSelectionUtility = mockk<LeaderSelectionUtility>()

	@InjectMockKs
	private val soknadsmottakerAPI = mockk<MottakerInterface>()

	@InjectMockKs
	private val fillagerAPI = mockk<FillagerInterface>()

	@InjectMockKs
	private val pdlInterface = mockk<PdlInterface>()


	private fun lagSoknadService(): SoknadService = SoknadService(
		skjemaService = skjemaService,
		repo = repo,
		vedleggService = vedleggService,
		ettersendingService = ettersendingService,
		filService = filService,
		brukernotifikasjonPublisher = brukernotifikasjonPublisher,
		innsenderMetrics = innsenderMetrics,
		exceptionHelper = exceptionHelper
	)

	private fun lagInnsendingService(soknadService: SoknadService): InnsendingService = InnsendingService(
		repo = repo,
		soknadService = soknadService,
		filService = filService,
		vedleggService = vedleggService,
		soknadsmottakerAPI = soknadsmottakerAPI,
		restConfig = restConfig,
		fillagerAPI = fillagerAPI,
		exceptionHelper = exceptionHelper,
		ettersendingService = ettersendingService,
		brukernotifikasjonPublisher = brukernotifikasjonPublisher,
		innsenderMetrics = innsenderMetrics,
		pdlInterface = pdlInterface
	)


	private val defaultSkjemanr = "NAV 55-00.60"

	@Test
	fun testSlettingAvInnsendteSoknader() {
		val soknadService = lagSoknadService()
		val innsendingService = lagInnsendingService(soknadService)
		val startFinnOgSlettArkiverteSoknader = SlettArkiverteSoknader(leaderSelectionUtility, soknadService)

		val soknader = mutableListOf<DokumentSoknadDto>()
		every { brukernotifikasjonPublisher.soknadStatusChange(capture(soknader)) } returns true
		every { leaderSelectionUtility.isLeader() } returns true
		every { soknadsmottakerAPI.sendInnSoknad(any(), any()) } returns Unit
		every { fillagerAPI.lagreFiler(any(), any()) } returns Unit
		every { pdlInterface.hentPersonIdents(any()) } returns listOf(IdentDto("123456789", "FOLKEREGISTERIDENT", false))
		every { pdlInterface.hentPersonData(any()) } returns PersonDto("123456789", "Fornavn", null, "Etternavn")

		val spraak = "no"
		val tema = "BID"

		val skalSendeInnOgArkivereId = soknadService.opprettNySoknad(
			Hjelpemetoder.lagDokumentSoknad(
				brukerId = "12345678901", skjemanr = defaultSkjemanr, spraak = spraak, tittel = "En test",
				tema = tema, id = null, innsendingsid = null, soknadsStatus = SoknadsStatusDto.opprettet, vedleggsListe = null,
				ettersendingsId = null, OffsetDateTime.now().minusDays(1)
			)
		)

		val skalSendeInnIkkeArkivereId = soknadService.opprettNySoknad(
			Hjelpemetoder.lagDokumentSoknad(
				brukerId = "12345678901", skjemanr = defaultSkjemanr, spraak = spraak, tittel = "En test",
				tema = tema, id = null, innsendingsid = null, soknadsStatus = SoknadsStatusDto.opprettet, vedleggsListe = null,
				ettersendingsId = null, OffsetDateTime.now().minusDays(1)
			)
		)

		val initAntall = innsenderMetrics.operationsCounterGet(InnsenderOperation.SLETT.name, tema) ?: 0.0
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
		Assertions.assertTrue(beholdtSoknad != null && beholdtSoknad.status == SoknadsStatusDto.innsendt)

		// Og metrics for antall slettede søknader er økt med 1
		Assertions.assertEquals(
			initAntall + 1.0,
			innsenderMetrics.operationsCounterGet(InnsenderOperation.SLETT.name, tema)
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
		assertTrue(kvittering != null)
	}

	private fun simulerArkiveringsRespons(innsendingsId: String, arkiveringsStatus: ArkiveringsStatus) {
		repo.setArkiveringsstatus(innsendingsId, arkiveringsStatus)
	}

}

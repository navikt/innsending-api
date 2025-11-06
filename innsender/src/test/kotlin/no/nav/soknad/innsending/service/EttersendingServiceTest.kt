package no.nav.soknad.innsending.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.model.AktivSakDto
import no.nav.soknad.innsending.model.InnsendtVedleggDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.HendelseRepository
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.testpersonid
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.SoknadAssertions
import no.nav.soknad.innsending.utils.builders.ettersending.InnsendtVedleggDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.OpprettEttersendingTestBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime

class EttersendingServiceTest : ApplicationTest() {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	private lateinit var skjemaService: SkjemaService

	@Autowired
	private lateinit var exceptionHelper: ExceptionHelper

	@Autowired
	private lateinit var soknadService: SoknadService

	@Autowired
	private lateinit var vedleggService: VedleggService

	@Autowired
	private lateinit var tilleggstonadService: TilleggsstonadService

	@Autowired
	private lateinit var hendelseRepository: HendelseRepository

	@Autowired
	private lateinit var filService: FilService

	@Autowired
	private lateinit var kodeverkService: KodeverkService

	@Autowired
	private lateinit var restConfig: RestConfig

	@Autowired
	private lateinit var safService: SafService

	@Autowired
	private lateinit var tilgangskontroll: Tilgangskontroll

	private val soknadsmottakerAPI = mockk<MottakerInterface>()

	private val pdlInterface = mockk<PdlInterface>()

	@MockkBean
	private lateinit var subjectHandler: SubjectHandlerInterface

	@BeforeEach
	fun setUp() {
		every { pdlInterface.hentPersonData(any()) } returns PersonDto("1234567890", "Kan", null, "Søke")
		every { subjectHandler.getClientId() } returns "application"
	}

	private fun lagEttersendingService(): EttersendingService = EttersendingService(
		repo = repo,
		skjemaService = skjemaService,
		exceptionHelper = exceptionHelper,
		innsenderMetrics = innsenderMetrics,
		soknadService = soknadService,
		vedleggService = vedleggService,
		safService = safService,
		tilgangskontroll = tilgangskontroll,
		kodeverkService = kodeverkService,
		subjectHandler = subjectHandler,
	)

	private fun lagInnsendingService(): InnsendingService = InnsendingService(
		soknadService = soknadService,
		repo = repo,
		vedleggService = vedleggService,
		tilleggstonadService = tilleggstonadService,
		ettersendingService = lagEttersendingService(),
		filService = filService,
		innsenderMetrics = innsenderMetrics,
		exceptionHelper = exceptionHelper,
		soknadsmottakerAPI = soknadsmottakerAPI,
		restConfig = restConfig,
		pdlInterface = pdlInterface,
	)

	@Test
	fun `Skal opprette ettersending hvis det mangler paakrevde vedlegg`() {
		// Gitt
		val brukerid = testpersonid
		val skjemanr = "NAV 55-00.60"
		val spraak = "nb_NO"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		val ettersendingService = lagEttersendingService()

		// Når
		val ettersending = ettersendingService.sjekkOgOpprettEttersendingsSoknad(
			innsendtSoknadDto = dokumentSoknadDto,
			manglende = dokumentSoknadDto.vedleggsListe,
			soknadDtoInput = dokumentSoknadDto
		)

		// Så
		assertNotNull(ettersending)
	}

	@Test
	fun `Skal ikke opprette ettersendingssoknad hvis det ikke mangler paakrevde vedlegg`() {
		// Gitt
		val brukerid = testpersonid
		val skjemanr = "NAV 55-00.60"
		val spraak = "nb_NO"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		val ettersendingService = lagEttersendingService()
		val ettersendingServiceMock = spyk(ettersendingService)

		every { ettersendingServiceMock.saveEttersending(any(), any()) } answers { callOriginal() }

		// Når
		ettersendingServiceMock.sjekkOgOpprettEttersendingsSoknad(
			innsendtSoknadDto = dokumentSoknadDto,
			manglende = emptyList(),
			soknadDtoInput = dokumentSoknadDto
		)

		// Så
		verify(exactly = 0) { ettersendingServiceMock.saveEttersending(any(), any()) }
	}

	@Test
	fun opprettEttersendingGittArkivertSoknadTest() {

		val brukerid = testpersonid
		val skjemanr = "NAV 10-07.20"
		val tittel = "Test av ettersending gitt arkivertsoknad"
		val tema = "HJE"
		val arkivertInnsendingsId = "1234567890123345"
		val arkivertSoknad = AktivSakDto(
			skjemanr, tittel, tema,
			OffsetDateTime.now().minusDays(2L), false,
			listOf(
				InnsendtVedleggDto(skjemanr, tittel),
				InnsendtVedleggDto("C1", "Vedlegg til $tittel")
			),
			arkivertInnsendingsId
		)
		val ettersendingService = lagEttersendingService()
		val ettersending = OpprettEttersendingTestBuilder().vedleggsListe(
			listOf(
				InnsendtVedleggDtoTestBuilder().vedleggsnr("C1").tittel("Vedlegg1").build(),
				InnsendtVedleggDtoTestBuilder().vedleggsnr("N6").tittel("Vedlegg2").build(),
				InnsendtVedleggDtoTestBuilder().vedleggsnr("L8").tittel("Vedlegg3").build(),
			)
		).build()

		val dokumentSoknadDto = ettersendingService.createEttersendingFromArchivedSoknad(
			brukerId = brukerid,
			archivedSoknad = arkivertSoknad,
			ettersending = ettersending,
			forsteInnsendingsDato = null
		)

		assertTrue(dokumentSoknadDto.innsendingsId != null && VisningsType.ettersending == dokumentSoknadDto.visningsType && dokumentSoknadDto.ettersendingsId == arkivertInnsendingsId)
		assertTrue(dokumentSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(3, dokumentSoknadDto.vedleggsListe.size)
		assertTrue(!dokumentSoknadDto.vedleggsListe.any { it.erHoveddokument && it.vedleggsnr == skjemanr })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "C1" && it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt && it.innsendtdato != null })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "N6" && it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "L8" && it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt })

	}

	@Test
	fun opprettEttersendingGittSkjemanrTest() {

		val brukerid = testpersonid
		val skjemanr = "NAV 10-07.20"

		val ettersending = OpprettEttersendingTestBuilder()
			.skjemanr(skjemanr)
			.vedleggsListe(
				listOf(
					InnsendtVedleggDtoTestBuilder().vedleggsnr("C1").tittel("Vedlegg1").build(),
					InnsendtVedleggDtoTestBuilder().vedleggsnr("L8").tittel("Vedlegg3").build(),
				)
			).build()

		val ettersendingService = lagEttersendingService()

		val dokumentSoknadDto =
			ettersendingService.createEttersendingFromExistingSoknader(brukerid, ettersending)

		assertTrue(dokumentSoknadDto.innsendingsId != null && VisningsType.ettersending == dokumentSoknadDto.visningsType && dokumentSoknadDto.ettersendingsId == dokumentSoknadDto.innsendingsId)
		assertTrue(dokumentSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(2, dokumentSoknadDto.vedleggsListe.size)
		assertTrue(!dokumentSoknadDto.vedleggsListe.any { it.erHoveddokument && it.vedleggsnr == skjemanr })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "C1" && it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "L8" && it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt })

		val hendelseDbDatasEttersending2 =
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(dokumentSoknadDto.innsendingsId!!)
		assertTrue(hendelseDbDatasEttersending2.isNotEmpty())
		assertEquals(HendelseType.Opprettet, hendelseDbDatasEttersending2[0].hendelsetype)
	}

	@Test
	fun opprettEttersendingGittSoknadOgVedleggTest() {
		val ettersendingService = lagEttersendingService()
		val innsendingService = lagInnsendingService()
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "C1", "L8"))

		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument })
		)
		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.vedleggsnr == "W1" })
		)

		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isNotEmpty())
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty())

		val innsendtSoknadDto = soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)
		val ettersending = OpprettEttersendingTestBuilder().vedleggsListe(
			listOf(
				InnsendtVedleggDtoTestBuilder().vedleggsnr("N6").tittel("Vedlegg1").build(),
				InnsendtVedleggDtoTestBuilder().vedleggsnr("W1").tittel("Vedlegg2").build(),
			)
		).build()

		val ettersendingsSoknadDto = ettersendingService.createEttersendingFromInnsendtSoknad(
			brukerId = testpersonid,
			existingSoknad = innsendtSoknadDto,
			ettersending
		)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.innsendingsId != null && VisningsType.ettersending == ettersendingsSoknadDto.visningsType && ettersendingsSoknadDto.ettersendingsId == dokumentSoknadDto.innsendingsId)
		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(2, ettersendingsSoknadDto.vedleggsListe.size)
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.any { it.erHoveddokument && it.vedleggsnr == dokumentSoknadDto.skjemanr })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "N6" && it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "W1" && it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt && it.innsendtdato != null })

		val hendelseDbDatasEttersending2 =
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(ettersendingsSoknadDto.innsendingsId!!)
		assertTrue(hendelseDbDatasEttersending2.isNotEmpty())
		assertEquals(HendelseType.Opprettet, hendelseDbDatasEttersending2[0].hendelsetype)

	}

	@Test
	fun testFlereEttersendingerPaSoknad() {
		val innsendingService = lagInnsendingService()
		val ettersendingService = lagEttersendingService()

		// Opprett original soknad
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "W2"))

		// Laster opp skjema (hoveddokumentet) til soknaden
		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument })
		)

		// Sender inn original soknad
		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty())

		val soknader = soknadService.hentAktiveSoknader(listOf(dokumentSoknadDto.brukerId!!))
		assertTrue(soknader.isNotEmpty())

		val ettersending = OpprettEttersendingTestBuilder()
			.skjemanr(dokumentSoknadDto.skjemanr)
			.vedleggsListe(
				listOf(
					InnsendtVedleggDtoTestBuilder().vedleggsnr("W1").tittel("Vedlegg1").build(),
					InnsendtVedleggDtoTestBuilder().vedleggsnr("W2").tittel("Vedlegg2").build()
				)
			)
			.build()

		// Oppretter ettersendingssoknad
		val ettersendingsSoknadDto =
			ettersendingService.createEttersendingFromExistingSoknader(dokumentSoknadDto.brukerId!!, ettersending)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt })

		// Laster opp fil til vedlegg W1 til ettersendingssøknaden
		val lagretFil =
			filService.lagreFil(
				ettersendingsSoknadDto,
				Hjelpemetoder.lagFilDtoMedFil(ettersendingsSoknadDto.vedleggsListe.first {
					!it.erHoveddokument && it.vedleggsnr.equals(
						"W1",
						true
					)
				})
			)

		assertTrue(lagretFil.id != null)
		assertTrue(lagretFil.vedleggsid == ettersendingsSoknadDto.vedleggsListe.first {
			!it.erHoveddokument && it.vedleggsnr.equals(
				"W1",
				true
			)
		}.id)

		val ettersendingsKvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				ettersendingsSoknadDto,
				innsendingService
			)
		assertTrue(ettersendingsKvitteringsDto.hoveddokumentRef == null)
		assertTrue(ettersendingsKvitteringsDto.innsendteVedlegg!!.isNotEmpty())
		assertTrue(ettersendingsKvitteringsDto.skalEttersendes!!.isNotEmpty())

		// Oppretter ettersendingssoknad2
		val ettersendingsSoknadDto2 =
			ettersendingService.createEttersendingFromExistingSoknader(dokumentSoknadDto.brukerId!!, ettersending)

		assertTrue(ettersendingsSoknadDto2.vedleggsListe.isNotEmpty())

		assertEquals(2, ettersendingsSoknadDto2.vedleggsListe.count
		{ it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt })

		assertEquals(1, ettersendingsSoknadDto2.vedleggsListe.count { it.innsendtdato != null })
		assertEquals(1, ettersendingsSoknadDto2.vedleggsListe.count { it.innsendtdato == null })

		// Laster opp fil til vedlegg W1 til ettersendingssøknaden
		filService.lagreFil(
			ettersendingsSoknadDto2,
			Hjelpemetoder.lagFilDtoMedFil(ettersendingsSoknadDto2.vedleggsListe.first {
				!it.erHoveddokument && it.vedleggsnr.equals(
					"W2",
					true
				)
			})
		)

		val ettersendingsKvitteringsDto2 =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				ettersendingsSoknadDto2,
				innsendingService
			)
		assertTrue(ettersendingsKvitteringsDto2.hoveddokumentRef == null)
		assertEquals("W2", ettersendingsKvitteringsDto2.innsendteVedlegg!!.first().vedleggsnr)
		assertEquals("W1", ettersendingsKvitteringsDto2.skalEttersendes!!.first().vedleggsnr)

		val hendelseDbDatasEttersending2 =
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(ettersendingsSoknadDto2.innsendingsId!!)
		assertTrue(hendelseDbDatasEttersending2.size > 1)
		assertEquals(HendelseType.Opprettet, hendelseDbDatasEttersending2[0].hendelsetype)
		assertEquals(HendelseType.Innsendt, hendelseDbDatasEttersending2[1].hendelsetype)

		val vedleggDto = filService.hentFiler(
			ettersendingsSoknadDto2,
			ettersendingsSoknadDto2.innsendingsId!!,
			ettersendingsSoknadDto2.vedleggsListe.last().id!!,
			true
		)
		assertTrue(vedleggDto.isNotEmpty())
	}

	@Test
	fun opprettEttersending() {
		val innsendingService = lagInnsendingService()
		val ettersendingService = lagEttersendingService()

		// Opprett original soknad
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "W2"))

		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument })
		)

		// Sender inn original soknad
		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty())

		val hendelseDbDatasInnsendt =
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(dokumentSoknadDto.innsendingsId!!)
		assertTrue(hendelseDbDatasInnsendt.size > 1)
		assertEquals(HendelseType.Opprettet, hendelseDbDatasInnsendt[0].hendelsetype)
		assertEquals(HendelseType.Innsendt, hendelseDbDatasInnsendt[1].hendelsetype)

		val ettersending = OpprettEttersendingTestBuilder()
			.skjemanr(dokumentSoknadDto.skjemanr)
			.vedleggsListe(
				listOf(
					InnsendtVedleggDtoTestBuilder().vedleggsnr("W1").tittel("Vedlegg1").build(),
					InnsendtVedleggDtoTestBuilder().vedleggsnr("W2").tittel("Vedlegg2").build(),
				)
			).build()

		// Opprett ettersendingssoknad
		val ettersendingsSoknadDto =
			ettersendingService.createEttersendingFromExistingSoknader(dokumentSoknadDto.brukerId!!, ettersending)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.vedleggsListe.none { it.opplastingsStatus == OpplastingsStatusDto.Innsendt })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt })

		val hendelseDbDatasEttersending =
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(ettersendingsSoknadDto.innsendingsId!!)
		assertTrue(hendelseDbDatasEttersending.isNotEmpty())
		assertEquals(HendelseType.Opprettet, hendelseDbDatasEttersending[0].hendelsetype)

	}


	@Test
	fun opprettEttersendingGittArkivertSoknadOgVedleggTest() {
		val ettersendingService = lagEttersendingService()

		val brukerid = testpersonid
		val skjemanr = "NAV 10-07.20"
		val arkivertSoknad = AktivSakDto(
			skjemanr,
			"Tittel",
			"Tema",
			OffsetDateTime.now(),
			false,
			listOf(InnsendtVedleggDto(skjemanr, "Tittel")),
			Utilities.laginnsendingsId()
		)

		val ettersending = OpprettEttersendingTestBuilder()
			.skjemanr(skjemanr)
			.vedleggsListe(
				listOf(
					InnsendtVedleggDtoTestBuilder().vedleggsnr("C1").tittel("Vedlegg1").build(),
					InnsendtVedleggDtoTestBuilder().vedleggsnr("L8").tittel("Vedlegg2").build(),
				)
			).build()

		val dokumentSoknadDto = ettersendingService.createEttersendingFromArchivedSoknad(
			brukerId = brukerid,
			archivedSoknad = arkivertSoknad,
			ettersending = ettersending,
			null
		)

		assertTrue(dokumentSoknadDto.innsendingsId != null && VisningsType.ettersending == dokumentSoknadDto.visningsType && dokumentSoknadDto.ettersendingsId == arkivertSoknad.innsendingsId)
		assertTrue(dokumentSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(2, dokumentSoknadDto.vedleggsListe.size)
		assertTrue(!dokumentSoknadDto.vedleggsListe.any { it.erHoveddokument && it.vedleggsnr == skjemanr })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "C1" && it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "L8" && it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt })

		val hendelseDbDatasEttersending2 =
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(dokumentSoknadDto.innsendingsId!!)
		assertTrue(hendelseDbDatasEttersending2.isNotEmpty())
		assertEquals(HendelseType.Opprettet, hendelseDbDatasEttersending2[0].hendelsetype)
	}
}

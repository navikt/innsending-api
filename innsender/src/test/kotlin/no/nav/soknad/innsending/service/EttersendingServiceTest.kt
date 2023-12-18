package no.nav.soknad.innsending.service

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.BrukerNotifikasjonConfig
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.HendelseRepository
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.testpersonid
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.SoknadAssertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime

class EttersendingServiceTest : ApplicationTest() {

	@Autowired
	private val notifikasjonConfig: BrukerNotifikasjonConfig = BrukerNotifikasjonConfig()

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
	private lateinit var hendelseRepository: HendelseRepository

	@Autowired
	private lateinit var filService: FilService

	@Autowired
	private lateinit var restConfig: RestConfig

	@InjectMockKs
	private val soknadsmottakerAPI = mockk<MottakerInterface>()

	@InjectMockKs
	private val sendTilPublisher = mockk<PublisherInterface>()

	@InjectMockKs
	private val pdlInterface = mockk<PdlInterface>()

	private var brukernotifikasjonPublisher: BrukernotifikasjonPublisher? = null

	@BeforeEach
	fun setUp() {
		brukernotifikasjonPublisher = spyk(BrukernotifikasjonPublisher(notifikasjonConfig, sendTilPublisher))
		every { pdlInterface.hentPersonData(any()) } returns PersonDto("1234567890", "Kan", null, "Søke")
	}

	private fun lagEttersendingService(): EttersendingService = EttersendingService(
		repo = repo,
		skjemaService = skjemaService,
		exceptionHelper = exceptionHelper,
		brukerNotifikasjon = brukernotifikasjonPublisher!!,
		innsenderMetrics = innsenderMetrics,
		soknadService = soknadService,
		vedleggService = vedleggService
	)

	private fun lagInnsendingService(): InnsendingService = InnsendingService(
		soknadService = soknadService,
		repo = repo,
		vedleggService = vedleggService,
		ettersendingService = lagEttersendingService(),
		filService = filService,
		brukernotifikasjonPublisher = brukernotifikasjonPublisher!!,
		innsenderMetrics = innsenderMetrics,
		exceptionHelper = exceptionHelper,
		soknadsmottakerAPI = soknadsmottakerAPI,
		restConfig = restConfig,
		pdlInterface = pdlInterface,
	)

	@Test
	fun `Skal sende brukernotifikasjon med erSystemGenerert=true hvis det mangler paakrevde vedlegg`() {
		// Gitt
		val brukerid = testpersonid
		val skjemanr = "NAV 55-00.60"
		val spraak = "nb_NO"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		val ettersendingService = lagEttersendingService()

		val message = slot<AddNotification>()
		every { sendTilPublisher.opprettBrukernotifikasjon(capture(message)) } returns Unit

		// Når
		ettersendingService.sjekkOgOpprettEttersendingsSoknad(
			innsendtSoknadDto = dokumentSoknadDto,
			manglende = dokumentSoknadDto.vedleggsListe,
			soknadDtoInput = dokumentSoknadDto
		)

		// Så
		assertNotNull(message.captured)
		assertTrue(message.captured.soknadRef.erSystemGenerert == true)
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

		every { ettersendingServiceMock.opprettEttersendingsSoknad(any(), any()) } answers { callOriginal() }

		// Når
		ettersendingServiceMock.sjekkOgOpprettEttersendingsSoknad(
			innsendtSoknadDto = dokumentSoknadDto,
			manglende = emptyList(),
			soknadDtoInput = dokumentSoknadDto
		)

		// Så
		verify { ettersendingServiceMock.opprettEttersendingsSoknad(any(), any()) wasNot Called }
	}

	@Test
	fun opprettSoknadForEttersendingAvVedleggGittArkivertSoknadTest() {

		val brukerid = testpersonid
		val skjemanr = "NAV 10-07.20"
		val tittel = "Test av ettersending gitt arkivertsoknad"
		val spraak = "nb_NO"
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

		val dokumentSoknadDto = ettersendingService.opprettEttersendingGittArkivertSoknad(
			brukerid,
			arkivertSoknad,
			spraak,
			listOf("C1", "N6", "L8")
		)

		assertTrue(dokumentSoknadDto.innsendingsId != null && VisningsType.ettersending == dokumentSoknadDto.visningsType && dokumentSoknadDto.ettersendingsId == arkivertInnsendingsId)
		assertTrue(dokumentSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(3, dokumentSoknadDto.vedleggsListe.size)
		assertTrue(!dokumentSoknadDto.vedleggsListe.any { it.erHoveddokument && it.vedleggsnr == skjemanr })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "C1" && it.opplastingsStatus == OpplastingsStatusDto.innsendt && it.innsendtdato != null })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "N6" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "L8" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
	}

	@Test
	fun opprettSoknadForEttersendingGittSkjemanrTest() {

		val brukerid = testpersonid
		val skjemanr = "NAV 10-07.20"
		val spraak = "nb_NO"

		val ettersendingService = lagEttersendingService()

		val dokumentSoknadDto =
			ettersendingService.opprettEttersendingGittSkjemanr(brukerid, skjemanr, spraak, listOf("C1", "L8"))

		assertTrue(dokumentSoknadDto.innsendingsId != null && VisningsType.ettersending == dokumentSoknadDto.visningsType && dokumentSoknadDto.ettersendingsId == dokumentSoknadDto.innsendingsId)
		assertTrue(dokumentSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(2, dokumentSoknadDto.vedleggsListe.size)
		assertTrue(!dokumentSoknadDto.vedleggsListe.any { it.erHoveddokument && it.vedleggsnr == skjemanr })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "C1" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "L8" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })

		val hendelseDbDatasEttersending2 =
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(dokumentSoknadDto.innsendingsId!!)
		assertTrue(hendelseDbDatasEttersending2.isNotEmpty())
		assertEquals(HendelseType.Opprettet, hendelseDbDatasEttersending2[0].hendelsetype)
	}

	@Test
	fun opprettSoknadForEttersendingGittSoknadOgVedleggTest() {
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

		val ettersendingsSoknadDto = ettersendingService.opprettEttersendingGittSoknadOgVedlegg(
			brukerId = testpersonid,
			nyesteSoknad = innsendtSoknadDto,
			sprak = "nb_NO",
			vedleggsnrListe = listOf("N6", "W1")
		)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.innsendingsId != null && VisningsType.ettersending == ettersendingsSoknadDto.visningsType && ettersendingsSoknadDto.ettersendingsId == dokumentSoknadDto.innsendingsId)
		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(4, ettersendingsSoknadDto.vedleggsListe.size)
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.any { it.erHoveddokument && it.vedleggsnr == dokumentSoknadDto.skjemanr })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "N6" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "C1" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "L8" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "W1" && it.opplastingsStatus == OpplastingsStatusDto.innsendt && it.innsendtdato != null })

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

		val soknader = soknadService.hentAktiveSoknader(listOf(dokumentSoknadDto.brukerId))
		assertTrue(soknader.isNotEmpty())

		// Oppretter ettersendingssoknad
		val ettersendingsSoknadDto =
			ettersendingService.opprettEttersending(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })

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
			ettersendingService.opprettEttersending(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto2.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto2.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertEquals(
			1,
			ettersendingsSoknadDto2.vedleggsListe.count { it.opplastingsStatus == OpplastingsStatusDto.innsendt })
		assertTrue(ettersendingsSoknadDto2.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt }
			.all { it.innsendtdato != null })
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
		assertTrue(ettersendingsKvitteringsDto2.innsendteVedlegg!!.isNotEmpty())
		assertTrue(ettersendingsKvitteringsDto2.skalEttersendes!!.isEmpty())

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
	fun opprettSoknadForettersendingAvVedlegg() {
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

		// Opprett ettersendingssoknad
		val ettersendingsSoknadDto =
			ettersendingService.opprettEttersending(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.vedleggsListe.none { it.opplastingsStatus == OpplastingsStatusDto.innsendt })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })

		val hendelseDbDatasEttersending =
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(ettersendingsSoknadDto.innsendingsId!!)
		assertTrue(hendelseDbDatasEttersending.isNotEmpty())
		assertEquals(HendelseType.Opprettet, hendelseDbDatasEttersending[0].hendelsetype)

	}


	@Test
	fun opprettSoknadForEttersendingGittArkivertSoknadOgVedleggTest() {
		val ettersendingService = lagEttersendingService()

		val brukerid = testpersonid
		val skjemanr = "NAV 10-07.20"
		val spraak = "nb_NO"
		val arkivertSoknad = AktivSakDto(
			skjemanr,
			"Tittel",
			"Tema",
			OffsetDateTime.now(),
			false,
			listOf(InnsendtVedleggDto(skjemanr, "Tittel")),
			Utilities.laginnsendingsId()
		)
		val opprettEttersendingGittSkjemaNr = OpprettEttersendingGittSkjemaNr(skjemanr, spraak, listOf("C1", "L8"))

		val dokumentSoknadDto = ettersendingService.opprettEttersendingGittArkivertSoknadOgVedlegg(
			brukerid,
			arkivertSoknad,
			opprettEttersendingGittSkjemaNr,
			spraak,
			null
		)

		assertTrue(dokumentSoknadDto.innsendingsId != null && VisningsType.ettersending == dokumentSoknadDto.visningsType && dokumentSoknadDto.ettersendingsId == arkivertSoknad.innsendingsId)
		assertTrue(dokumentSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(2, dokumentSoknadDto.vedleggsListe.size)
		assertTrue(!dokumentSoknadDto.vedleggsListe.any { it.erHoveddokument && it.vedleggsnr == skjemanr })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "C1" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "L8" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })

		val hendelseDbDatasEttersending2 =
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(dokumentSoknadDto.innsendingsId!!)
		assertTrue(hendelseDbDatasEttersending2.isNotEmpty())
		assertEquals(HendelseType.Opprettet, hendelseDbDatasEttersending2[0].hendelsetype)
	}
}

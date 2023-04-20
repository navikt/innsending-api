package no.nav.soknad.innsending.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import io.mockk.slot
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.skjema.SkjemaClient
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.testpersonid
import no.nav.soknad.innsending.utils.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*


@SpringBootTest
@ActiveProfiles("spring")
@EnableTransactionManagement
class SoknadServiceTest {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	private lateinit var soknadService: SoknadService

	@Autowired
	private lateinit var hentSkjemaDataConsumer: HentSkjemaDataConsumer

	@Autowired
	private lateinit var soknadRepository: SoknadRepository

	@Autowired
	private lateinit var vedleggRepository: VedleggRepository

	@Autowired
	private lateinit var filRepository: FilRepository

	@Autowired
	private lateinit var vedleggService: VedleggService

	@Autowired
	private lateinit var ettersendingService: EttersendingService

	@Autowired
	private lateinit var filService: FilService

	@Autowired
	private lateinit var exceptionHelper: ExceptionHelper

	@Autowired
	private lateinit var restConfig: RestConfig

	@InjectMockKs
	private val brukernotifikasjonPublisher = mockk<BrukernotifikasjonPublisher>()

	@InjectMockKs
	private val hentSkjemaData = mockk<SkjemaClient>()

	@InjectMockKs
	private val fillagerAPI = mockk<FillagerInterface>()

	@InjectMockKs
	private val soknadsmottakerAPI = mockk<MottakerInterface>()

	@InjectMockKs
	private val pdlInterface = mockk<PdlInterface>()


	private val defaultSkjemanr = "NAV 55-00.60"

	@BeforeEach
	fun setup() {
		every { hentSkjemaData.hent() } returns hentSkjemaDataConsumer.initSkjemaDataFromDisk()
		every { brukernotifikasjonPublisher.soknadStatusChange(any()) } returns true
		every { pdlInterface.hentPersonData(any()) } returns PersonDto("1234567890", "Kan", null, "Søke")
	}


	@AfterEach
	fun ryddOpp() {
		filRepository.deleteAll()
		vedleggRepository.deleteAll()
		soknadRepository.deleteAll()
	}


	private fun lagInnsendingService(soknadService: SoknadService): InnsendingService = InnsendingService(
		soknadService = soknadService,
		repo = repo,
		vedleggService = vedleggService,
		ettersendingService = ettersendingService,
		filService = filService,
		brukernotifikasjonPublisher = brukernotifikasjonPublisher,
		innsenderMetrics = innsenderMetrics,
		exceptionHelper = exceptionHelper,
		soknadsmottakerAPI = soknadsmottakerAPI,
		restConfig = restConfig,
		fillagerAPI = fillagerAPI,
		pdlInterface = pdlInterface,
	)

	@Test
	fun opprettSoknadGittSkjemanr() {
		val brukerid = testpersonid
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak)
		assertNotNull(dokumentSoknadDto.innsendingsId)
	}

	@Test
	fun opprettSoknadGittSkjemanrOgIkkeStottetSprak() {
		val brukerid = testpersonid
		val skjemanr = defaultSkjemanr
		val skjemaTittel_en = hentSkjemaDataConsumer.hentSkjemaEllerVedlegg(defaultSkjemanr, "en").tittel
		val spraak = "fr"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak) // Beholder ønsket språk
		assertEquals(skjemaTittel_en, dokumentSoknadDto.tittel) // engelsk backup for fransk
		assertNotNull(dokumentSoknadDto.innsendingsId)
	}


	@Test
	fun opprettSoknadGittUkjentSkjemanrKasterException() {
		val brukerid = testpersonid
		val skjemanr = "NAV XX-00.11"
		val spraak = "nb_NO"
		val exception = assertThrows(
			ResourceNotFoundException::class.java,
			{ soknadService.opprettSoknad(brukerid, skjemanr, spraak) },
			"ResourceNotFoundException was expected"
		)

		assertEquals("Skjema med id = $skjemanr ikke funnet", exception.message)
	}

	@Test
	fun opprettSoknadGittSoknadDokument() {
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf())

		assertTrue(dokumentSoknadDto != null)
	}


	@Test
	fun opprettSoknadForettersendingAvVedlegg() {
		val innsendingService = lagInnsendingService(soknadService)
		// Opprett original soknad
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "W2"))

		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument })
		)

		// Sender inn original soknad
		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				fillagerAPI,
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty())

		// Opprett ettersendingssoknad
		val ettersendingsSoknadDto =
			soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.vedleggsListe.none { it.opplastingsStatus == OpplastingsStatusDto.innsendt })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
	}

	@Test
	fun testAtFilerIkkeSlettesVedInnsending() {
		val innsendingService = lagInnsendingService(soknadService)
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		// Laster opp skjema (hoveddokumentet) til soknaden
		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument })
		)

		// laster opp fil til vedlegget
		val lagretFilForVedlegg =
			filService.lagreFil(dokumentSoknadDto, Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first {
				!it.erHoveddokument && it.vedleggsnr.equals(
					"W1",
					true
				)
			}))

		assertTrue(lagretFilForVedlegg.id != null)
		assertTrue(lagretFilForVedlegg.vedleggsid == dokumentSoknadDto.vedleggsListe.first {
			!it.erHoveddokument && it.vedleggsnr.equals(
				"W1",
				true
			)
		}.id)

		// Sender inn søknaden
		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				fillagerAPI,
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isNotEmpty())
		assertTrue(kvitteringsDto.skalEttersendes!!.isEmpty())

		// Test at filen til hoveddokumentet ikke er slettet
		val filerForHovedDokument = filService.hentFiler(
			dokumentSoknadDto,
			dokumentSoknadDto.innsendingsId!!,
			(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument }).id!!,
			true
		)
		assertTrue(filerForHovedDokument.isNotEmpty())

		// Test at filen til vedlegget ikke er slettet
		val filerForVedlegg = filService.hentFiler(
			dokumentSoknadDto,
			dokumentSoknadDto.innsendingsId!!,
			lagretFilForVedlegg.vedleggsid,
			true
		)
		assertTrue(filerForVedlegg.isNotEmpty())
	}

	@Test
	fun testFlereEttersendingerPaSoknad() {
		val innsendingService = lagInnsendingService(soknadService)

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
				fillagerAPI,
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
			soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

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
				fillagerAPI,
				soknadsmottakerAPI,
				ettersendingsSoknadDto,
				innsendingService
			)
		assertTrue(ettersendingsKvitteringsDto.hoveddokumentRef == null)
		assertTrue(ettersendingsKvitteringsDto.innsendteVedlegg!!.isNotEmpty())
		assertTrue(ettersendingsKvitteringsDto.skalEttersendes!!.isNotEmpty())

		// Oppretter ettersendingssoknad2
		val ettersendingsSoknadDto2 =
			soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

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
				fillagerAPI,
				soknadsmottakerAPI,
				ettersendingsSoknadDto2,
				innsendingService
			)
		assertTrue(ettersendingsKvitteringsDto2.hoveddokumentRef == null)
		assertTrue(ettersendingsKvitteringsDto2.innsendteVedlegg!!.isNotEmpty())
		assertTrue(ettersendingsKvitteringsDto2.skalEttersendes!!.isEmpty())

		val vedleggDto = filService.hentFiler(
			ettersendingsSoknadDto2,
			ettersendingsSoknadDto2.innsendingsId!!,
			ettersendingsSoknadDto2.vedleggsListe.last().id!!,
			true
		)
		assertTrue(vedleggDto.isNotEmpty())
	}


	@Test
	fun opprettSoknadForettersendingAvVedleggGittArkivertSoknadTest_MedUkjentSkjemanr() {
		val arkiverteVedlegg: List<InnsendtVedleggDto> = listOf(
			InnsendtVedleggDto(
				vedleggsnr = "NAV 08-09.10",
				tittel = "Søknad om å beholde sykepenger under opphold i utlandet"
			)
		)

		val arkivertSoknad = AktivSakDto(
			"NAV 08-07.04D",
			"Søknad om Sykepenger",
			"SYK",
			mapTilOffsetDateTime(LocalDateTime.now(), -10L),
			ettersending = false,
			innsendingsId = UUID.randomUUID().toString(),
			innsendtVedleggDtos = arkiverteVedlegg
		)

		val ettersending = soknadService.opprettSoknadForEttersendingAvVedleggGittArkivertSoknad(
			brukerId = "1234",
			arkivertSoknad = arkivertSoknad,
			"no_NO",
			listOf("W1")
		)

		assertTrue(ettersending != null)
		assertEquals(2, ettersending.vedleggsListe.size)

	}

	@Test
	fun hentOpprettetSoknadDokument() {
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val dokumentSoknadDtoHentet = soknadService.hentSoknad(dokumentSoknadDto.id!!)

		assertEquals(dokumentSoknadDto.id, dokumentSoknadDtoHentet.id)
	}

	@Test
	fun hentIkkeEksisterendeSoknadDokumentKasterFeil() {
		val exception = assertThrows(
			ResourceNotFoundException::class.java,
			{ soknadService.hentSoknad("9999") },
			"ResourceNotFoundException was expected"
		)

		assertEquals(exception.message, "Ingen soknad med id = 9999 funnet")
	}

	@Test
	fun hentOpprettedeAktiveSoknadsDokument() {
		SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), testpersonid)
		SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), testpersonid)
		SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W2"), "12345678902")
		SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), "12345678903")

		val dokumentSoknadDtos = soknadService.hentAktiveSoknader(listOf(testpersonid, "12345678902"))

		assertTrue(dokumentSoknadDtos.filter { listOf(testpersonid, "12345678902").contains(it.brukerId) }.size == 3)
		assertTrue(dokumentSoknadDtos.none { listOf("12345678903").contains(it.brukerId) })
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

		val dokumentSoknadDto = soknadService.opprettSoknadForEttersendingAvVedleggGittArkivertSoknad(
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

		val dokumentSoknadDto =
			soknadService.opprettSoknadForEttersendingGittSkjemanr(brukerid, skjemanr, spraak, listOf("C1", "L8"))

		assertTrue(dokumentSoknadDto.innsendingsId != null && VisningsType.ettersending == dokumentSoknadDto.visningsType && dokumentSoknadDto.ettersendingsId == dokumentSoknadDto.innsendingsId)
		assertTrue(dokumentSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(2, dokumentSoknadDto.vedleggsListe.size)
		assertTrue(!dokumentSoknadDto.vedleggsListe.any { it.erHoveddokument && it.vedleggsnr == skjemanr })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "C1" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "L8" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
	}

	@Test
	fun opprettSoknadForEttersendingGittSoknadOgVedleggTest() {
		val innsendingService = lagInnsendingService(soknadService)
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
				fillagerAPI,
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isNotEmpty())
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty())

		val innsendtSoknadDto = soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)

		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedleggGittSoknadOgVedlegg(
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

	}


	@Test
	fun opprettSoknadForEttersendingGittArkivertSoknadOgVedleggTest() {

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

		val dokumentSoknadDto = soknadService.opprettSoknadForettersendingAvVedleggGittArkivertSoknadOgVedlegg(
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
	}


	@Test
	fun slettOpprettetSoknadDokument() {
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf())

		val slett = slot<List<VedleggDto>>()
		every { fillagerAPI.slettFiler(any(), capture(slett)) } returns Unit

		soknadService.slettSoknadAvBruker(dokumentSoknadDto)

		/*
				assertTrue(slett.isCaptured)
				assertTrue(slett.captured.size == dokumentSoknadDto.vedleggsListe.size)
		*/
		assertThrows<Exception> {
			soknadService.hentSoknad(dokumentSoknadDto.id!!)
		}
	}

	@Test
	fun lesOppTeksterTest() {
		val prop = Properties()
		val inputStream = SoknadServiceTest::class.java.getResourceAsStream("/tekster/innholdstekster_nb.properties")

		inputStream.use {
			prop.load(it)
		}
		assertTrue(!prop.isEmpty)
	}

	@Test
	fun opprettingAvSoknadVedKallFraFyllUt() {
		val tema = "HJE"
		val skjemanr = "NAV 10-07.04"
		val innsendingsId = soknadService.opprettNySoknad(lagDokumentSoknad(tema, skjemanr))

		val soknad = soknadService.hentSoknad(innsendingsId)

		assertTrue(soknad.tema == tema && soknad.skjemanr == skjemanr)
		assertTrue(soknad.vedleggsListe.size == 2)
		assertTrue(soknad.vedleggsListe.any { it.erHoveddokument && !it.erVariant && it.opplastingsStatus == OpplastingsStatusDto.lastetOpp })
	}


	@Test
	fun testAutomatiskSlettingAvGamleSoknader() {
		val brukerid = testpersonid
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"

		val dokumentSoknadDtoList = mutableListOf<DokumentSoknadDto>()
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak))
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak))

		soknadService.slettGamleSoknader(1L)
		dokumentSoknadDtoList.forEach { assertEquals(soknadService.hentSoknad(it.id!!).status, SoknadsStatusDto.opprettet) }

		soknadService.slettGamleSoknader(0L)
		dokumentSoknadDtoList.forEach {
			assertEquals(
				soknadService.hentSoknad(it.id!!).status,
				SoknadsStatusDto.automatiskSlettet
			)
		}
	}


	@Test
	fun testAutomatiskSlettingAvFilerTilInnsendteSoknader() {
		val innsendingService = lagInnsendingService(soknadService)
		val brukerid = testpersonid
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"

		val dokumentSoknadDtoList = mutableListOf<DokumentSoknadDto>()
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak, listOf("W1")))
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak, listOf("W1")))

		dokumentSoknadDtoList.forEach { it ->
			filService.lagreFil(
				it,
				Hjelpemetoder.lagFilDtoMedFil(it.vedleggsListe.first { it.erHoveddokument })
			)
		}
		dokumentSoknadDtoList.forEach { it ->
			filService.lagreFil(
				it,
				Hjelpemetoder.lagFilDtoMedFil(it.vedleggsListe.first { !it.erHoveddokument })
			)
		}

		val vedleggDtos = slot<List<VedleggDto>>()

		every { fillagerAPI.lagreFiler(any(), capture(vedleggDtos)) } returns Unit

		val soknad = slot<DokumentSoknadDto>()
		val vedleggDtos2 = slot<List<VedleggDto>>()
		every { soknadsmottakerAPI.sendInnSoknad(capture(soknad), capture(vedleggDtos2)) } returns Unit

		val kvitteringsDto = innsendingService.sendInnSoknad(dokumentSoknadDtoList[0])
		assertTrue(kvitteringsDto.hoveddokumentRef != null)

		val filDtos = filService.hentFiler(
			dokumentSoknadDtoList[0], dokumentSoknadDtoList[0].innsendingsId!!,
			dokumentSoknadDtoList[0].vedleggsListe.first { it.erHoveddokument && !it.erVariant }.id!!
		)
		assertTrue(filDtos.isNotEmpty())

		filService.slettfilerTilInnsendteSoknader(-1)
		val innsendtFilDtos = filService.hentFiler(
			dokumentSoknadDtoList[0], dokumentSoknadDtoList[0].innsendingsId!!,
			dokumentSoknadDtoList[0].vedleggsListe.first { it.erHoveddokument && !it.erVariant }.id!!
		)
		assertTrue(innsendtFilDtos.isEmpty())

		val ikkeInnsendtfilDtos =
			filService.hentFiler(
				dokumentSoknadDtoList[1], dokumentSoknadDtoList[1].innsendingsId!!,
				dokumentSoknadDtoList[1].vedleggsListe.first { it.erHoveddokument && !it.erVariant }.id!!
			)
		assertTrue(ikkeInnsendtfilDtos.isNotEmpty())

		val kvitteringsDto1 = innsendingService.sendInnSoknad(dokumentSoknadDtoList[1])
		assertTrue(kvitteringsDto1.hoveddokumentRef != null)

		filService.slettfilerTilInnsendteSoknader(1)
		val filDtos1 = filService.hentFiler(
			dokumentSoknadDtoList[1], dokumentSoknadDtoList[1].innsendingsId!!,
			dokumentSoknadDtoList[1].vedleggsListe.first { it.erHoveddokument && !it.erVariant }.id!!
		)
		assertTrue(filDtos1.isNotEmpty())

	}

	private fun lagVedleggDto(
		skjemanr: String, tittel: String, mimeType: String?, fil: ByteArray?, id: Long? = null,
		erHoveddokument: Boolean? = true, erVariant: Boolean? = false, erPakrevd: Boolean? = true
	): VedleggDto {
		return VedleggDto(
			tittel, tittel, erHoveddokument!!, erVariant!!,
			"application/pdf".equals(mimeType, true), erPakrevd!!,
			if (fil != null) OpplastingsStatusDto.lastetOpp else OpplastingsStatusDto.ikkeValgt, OffsetDateTime.now(), id,
			skjemanr, "Beskrivelse", UUID.randomUUID().toString(),
			if ("application/json" == mimeType) Mimetype.applicationSlashJson else if (mimeType != null) Mimetype.applicationSlashPdf else null,
			fil, null
		)
	}

	private fun lagDokumentSoknad(
		brukerId: String,
		skjemanr: String,
		spraak: String,
		tittel: String,
		tema: String
	): DokumentSoknadDto {
		val vedleggDtoPdf =
			lagVedleggDto(skjemanr, tittel, "application/pdf", Hjelpemetoder.getBytesFromFile("/litenPdf.pdf"))

		val vedleggDtoJson =
			lagVedleggDto(skjemanr, tittel, "application/json", Hjelpemetoder.getBytesFromFile("/sanity.json"))

		val vedleggDtoList = listOf(vedleggDtoPdf, vedleggDtoJson)
		return DokumentSoknadDto(
			brukerId, skjemanr, tittel, tema, SoknadsStatusDto.opprettet, OffsetDateTime.now(),
			vedleggDtoList, 1L, UUID.randomUUID().toString(), null, spraak,
			OffsetDateTime.now(), null, 0, VisningsType.fyllUt
		)
	}


	private fun lagDokumentSoknad(tema: String, skjemanr: String): DokumentSoknadDto {
		return lagDokumentSoknad(testpersonid, skjemanr, "nb_NO", "Fullmaktsskjema", tema)
	}
}

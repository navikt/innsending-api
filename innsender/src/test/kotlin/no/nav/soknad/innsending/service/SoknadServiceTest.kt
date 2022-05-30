package no.nav.soknad.innsending.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import io.mockk.slot
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.skjema.SkjemaClient
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.util.testpersonid
import no.nav.soknad.pdfutilities.PdfGenerator
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.util.*
import no.nav.soknad.innsending.utils.lagVedlegg
import org.junit.jupiter.api.*


@SpringBootTest
@ActiveProfiles("test")
@EnableTransactionManagement
class SoknadServiceTest {

	@Autowired
	private lateinit var soknadRepository: SoknadRepository

	@Autowired
	private lateinit var vedleggRepository: VedleggRepository

	@Autowired
	private lateinit var filRepository: FilRepository

	@Autowired
	private lateinit var skjemaService: HentSkjemaDataConsumer

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@InjectMockKs
	private val brukernotifikasjonPublisher = mockk<BrukernotifikasjonPublisher>()

	@InjectMockKs
	private val hentSkjemaData = mockk<SkjemaClient>()

	@InjectMockKs
	private val fillagerAPI = mockk<FillagerInterface>()

	@InjectMockKs
	private val soknadsmottakerAPI = mockk<MottakerInterface>()


	@BeforeEach
	fun setup() {
		every { hentSkjemaData.hent() } returns skjemaService.initSkjemaDataFromDisk()
		every { brukernotifikasjonPublisher.soknadStatusChange(any()) } returns true
	}


	@AfterEach
	fun ryddOpp() {
		filRepository.deleteAll()
		vedleggRepository.deleteAll()
		soknadRepository.deleteAll()
	}

	@Test
	fun opprettSoknadGittSkjemanr() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val brukerid = testpersonid
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak)
		assertNotNull(dokumentSoknadDto.innsendingsId)
	}

	@Test
	fun opprettSoknadGittSkjemanrOgIkkeStottetSprak() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val brukerid = testpersonid
		val skjemanr = "NAV 14-05.07"
		val spraak = "fr"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak) // Beholder ønsket språk
		assertEquals("Application for lump-sum grant at birth", dokumentSoknadDto.tittel) // engelsk backup for fransk
		assertNotNull(dokumentSoknadDto.innsendingsId)
	}


	private fun languageCode() {
		Locale.FRENCH
	}

	@Test
	fun opprettSoknadGittUkjentSkjemanrKasterException() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val brukerid = testpersonid
		val skjemanr = "NAV XX-00.11"
		val spraak = "no"
		val exception = assertThrows(
				ResourceNotFoundException::class.java,
				{ soknadService.opprettSoknad(brukerid, skjemanr, spraak) },
				"ResourceNotFoundException was expected")

		assertEquals("Skjema med id = $skjemanr ikke funnet", exception.message)

	}

	@Test
	fun opprettSoknadGittSoknadDokument() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf())

		assertTrue(dokumentSoknadDto != null)
	}


	@Test
	fun opprettSoknadForettersendingAvVedlegg() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		// Sender inn original soknad
		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

		// Opprett ettersendingssoknad
		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto != null)
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.isEmpty())
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt }.toList().isEmpty())
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt }.toList().isEmpty())

	}

	@Test
	fun testFlereEttersendingerPaSoknad() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "W2"))

		// Laster opp skjema (hoveddokumentet) til soknaden
		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		// Sender inn original soknad
		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

		// Oppretter ettersendingssoknad
		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(!ettersendingsSoknadDto.vedleggsListe.isEmpty())
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt }.toList().isEmpty())

		// Laster opp fil til vedlegg W1 til ettersendingssøknaden
		val lagretFil = soknadService.lagreFil(ettersendingsSoknadDto
			, lagFilDtoMedFil(ettersendingsSoknadDto.vedleggsListe.filter {!it.erHoveddokument && it.vedleggsnr.equals("W1", true)}.first()))

		assertTrue( lagretFil.id != null && lagretFil.data != null)
		assertTrue(lagretFil.vedleggsid == ettersendingsSoknadDto.vedleggsListe.filter {!it.erHoveddokument && it.vedleggsnr.equals("W1", true)}.first().id)

		testOgSjekkInnsendingAvSoknad(soknadService, ettersendingsSoknadDto)

		// Oppretter ettersendingssoknad2
		val ettersendingsSoknadDto2 = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto2 != null)
		assertTrue(!ettersendingsSoknadDto2.vedleggsListe.isEmpty())
		assertTrue(!ettersendingsSoknadDto2.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt }.toList().isEmpty())
		assertTrue(ettersendingsSoknadDto2.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt }.toList().count() == 2 )

		// Laster opp fil til vedlegg W1 til ettersendingssøknaden
		soknadService.lagreFil(ettersendingsSoknadDto2
			, lagFilDtoMedFil(ettersendingsSoknadDto2.vedleggsListe.filter {!it.erHoveddokument && it.vedleggsnr.equals("W2", true)}.first()))

		testOgSjekkInnsendingAvSoknad(soknadService, ettersendingsSoknadDto2)
	}

	@Test
	fun hentOpprettetSoknadDokument() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val dokumentSoknadDtoHentet = soknadService.hentSoknad(dokumentSoknadDto.id!!)

		assertEquals(dokumentSoknadDto.id, dokumentSoknadDtoHentet.id)
	}

	@Test
	fun hentIkkeEksisterendeSoknadDokumentKasterFeil() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val exception = assertThrows(
			ResourceNotFoundException::class.java,
			{ soknadService.hentSoknad("9999") },
			"ResourceNotFoundException was expected")

		assertEquals(exception.message, "Ingen soknad med id = 9999 funnet")
	}

	@Test
	fun hentOpprettedeAktiveSoknadsDokument() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), testpersonid)
		testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), testpersonid)
		testOgSjekkOpprettingAvSoknad(soknadService, listOf("W2"), "12345678902")
		testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), "12345678903")

		val dokumentSoknadDtos = soknadService.hentAktiveSoknader(listOf(testpersonid, "12345678902"))

		assertTrue(dokumentSoknadDtos.filter { listOf(testpersonid, "12345678902").contains(it.brukerId)}.size == 3)
		assertTrue(dokumentSoknadDtos.filter { listOf("12345678903").contains(it.brukerId)}.size == 0)

	}

	@Test
	fun hentOpprettetVedlegg() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.hentFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns dokumentSoknadDto.vedleggsListe

		val vedleggDto = soknadService.hentVedleggDto(dokumentSoknadDto.vedleggsListe[0].id!!)

		assertEquals(vedleggDto.id, dokumentSoknadDto.vedleggsListe[0].id!!)

		val filDtoListe = soknadService.hentFiler(dokumentSoknadDto, dokumentSoknadDto.innsendingsId!!, vedleggDto.id!!, false)
		assertTrue(filDtoListe.isEmpty())
	}

	@Test
	fun leggTilVedlegg() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.hentFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns dokumentSoknadDto.vedleggsListe

		val lagretVedleggDto = soknadService.leggTilVedlegg(dokumentSoknadDto)

		assertTrue(lagretVedleggDto != null && lagretVedleggDto.id != null && lagretVedleggDto.vedleggsnr == "N6")
	}

	@Test
	fun oppdaterVedleggEndrerKunTittelOgLabel() {
		// Når søker har endret label på et vedlegg av type annet (N6), skal tittel settes lik label og vedlegget i databasen oppdateres med disse endringene.
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.hentFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns dokumentSoknadDto.vedleggsListe

		val lagretVedleggDto = soknadService.leggTilVedlegg(dokumentSoknadDto)
		assertTrue(lagretVedleggDto != null && lagretVedleggDto.id != null)

		val endretVedleggDto = lagVedlegg(lagretVedleggDto.id, "XX", lagretVedleggDto.tittel, lagretVedleggDto.opplastingsStatus, lagretVedleggDto.erHoveddokument, null, "Ny tittel")
		val oppdatertVedleggDto = soknadService.endreVedlegg(endretVedleggDto, dokumentSoknadDto)

		assertTrue(oppdatertVedleggDto != null && oppdatertVedleggDto.id == lagretVedleggDto.id && oppdatertVedleggDto.tittel == "Ny tittel" && oppdatertVedleggDto.vedleggsnr == "N6"
			&& oppdatertVedleggDto.label == oppdatertVedleggDto.tittel)
	}

	@Test
	fun slettOpprettetSoknadDokument() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf())

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
	fun slettOpprettetVedlegg() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val vedleggsnr = "N6"
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf(vedleggsnr))

		val lagretVedlegg = dokumentSoknadDto.vedleggsListe.first { e -> vedleggsnr.equals(e.vedleggsnr) }

		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.slettFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns Unit

		soknadService.slettVedlegg(dokumentSoknadDto, lagretVedlegg.id!!)

/* Sender ikke opplastede filer fortløpende til soknadsfillager
		assertTrue(vedleggDtos.isCaptured)
		assertTrue(vedleggDtos.captured.get(0).id == dokumentSoknadDto.vedleggsListe[1].id)
*/

		assertThrows<Exception> {
			soknadService.hentVedleggDto(lagretVedlegg.id!!)
		}
	}


	@Test
	fun sendInnSoknad() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

		assertThrows<IllegalActionException> {
			soknadService.leggTilVedlegg(dokumentSoknadDto)
		}
		soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)

	}

	@Test
	fun sendInnSoknadFeilerUtenOpplastetHoveddokument() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		assertThrows<IllegalActionException> {
			soknadService.sendInnSoknad(dokumentSoknadDto)
		}
		soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)
	}

	@Test
	fun sendInnEttersendingsSoknadFeilerUtenOpplastetVedlegg() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(!ettersendingsSoknadDto.vedleggsListe.isEmpty())
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt }.toList().isEmpty())

		assertThrows<IllegalActionException> {
			soknadService.sendInnSoknad(ettersendingsSoknadDto)
		}
	}


	private fun testOgSjekkOpprettingAvSoknad(soknadService: SoknadService, vedleggsListe: List<String> = listOf(), brukerid: String = testpersonid): DokumentSoknadDto {
		return testOgSjekkOpprettingAvSoknad(soknadService, vedleggsListe, brukerid, "no" )
	}

	private fun testOgSjekkOpprettingAvSoknad(soknadService: SoknadService, vedleggsListe: List<String> = listOf(), brukerid: String = testpersonid, spraak: String = "no"): DokumentSoknadDto {
		val skjemanr = "NAV 95-00.11"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak, vedleggsListe)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak)
		assertTrue(dokumentSoknadDto.innsendingsId != null)
		assertTrue(dokumentSoknadDto.vedleggsListe.size == vedleggsListe.size+1)

		return dokumentSoknadDto

	}

	private fun testOgSjekkOpprettingAvSoknad(soknadService: SoknadService, vedleggsListe: List<String> = listOf()): DokumentSoknadDto {
		return testOgSjekkOpprettingAvSoknad(soknadService, vedleggsListe, testpersonid)
	}

	private fun testOgSjekkInnsendingAvSoknad(soknadService: SoknadService, dokumentSoknadDto: DokumentSoknadDto) {
		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.lagreFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns Unit

		val soknad = slot<DokumentSoknadDto>()
		val vedleggDtos2 = slot<List<VedleggDto>>()
		every { soknadsmottakerAPI.sendInnSoknad(capture(soknad), capture(vedleggDtos2)) } returns Unit

		val kvitteringsDto = soknadService.sendInnSoknad(dokumentSoknadDto)

		assertTrue(vedleggDtos.isCaptured)
		assertTrue(vedleggDtos.captured.size >= 1)

		assertTrue(soknad.isCaptured)
		assertTrue(soknad.captured.innsendingsId == dokumentSoknadDto.innsendingsId)

		assertTrue(kvitteringsDto.innsendingsId == dokumentSoknadDto.innsendingsId )

	}

	@Test
	fun lastOppFilTilVedlegg() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = dokumentSoknadDto.vedleggsListe.filter { "W1".equals(it.vedleggsnr) }.first()
		assertTrue(vedleggDto != null)

		val filDtoSaved = soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(vedleggDto))

		assertTrue(filDtoSaved != null)
		assertTrue(filDtoSaved.id != null)

		val hentetFilDto = soknadService.hentFil(dokumentSoknadDto, vedleggDto.id!!, filDtoSaved.id!!)
		assertTrue(filDtoSaved.id == hentetFilDto.id)

	}

	@Test
	fun slettFilTilVedlegg() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = dokumentSoknadDto.vedleggsListe.filter { "W1".equals(it.vedleggsnr) }.first()
		assertTrue(vedleggDto != null)

		val filDtoSaved = soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(vedleggDto))

		assertTrue(filDtoSaved != null)
		assertTrue(filDtoSaved.id != null)

		soknadService.slettFil(dokumentSoknadDto, filDtoSaved.vedleggsid, filDtoSaved.id!!)

	}
	@Test
	fun lesOppTeksterTest() {
		val prop = Properties()
		val inputStream	=  SoknadServiceTest::class.java.getResourceAsStream("/tekster/innholdstekster_nb.properties")

		inputStream.use {
			prop.load(it)
		}
		assertTrue(!prop.isEmpty)
	}

	@Test
	fun lagKvitteringsHoveddokument() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		// Sender inn original soknad
		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

		// Test generering av kvittering for innsendt soknad
		val innsendtSoknad = soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)
		val kvitteringsDokument = PdfGenerator().lagKvitteringsSide(innsendtSoknad, "Per Person")
		assertTrue(kvitteringsDokument != null)

		// Skriver til tmp fil for manuell sjekk av innholdet av generert PDF
		//writeBytesToFile("dummy", ".pdf", kvitteringsDokument)

	}


	@Test
	fun lagDummyHoveddokumentForEttersending() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		// Sender inn original soknad
		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

		// Opprett ettersendingssoknad
		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto != null)
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.isEmpty())
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt }.toList().isEmpty())
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt }.toList().isEmpty())

		val dummyHovedDokument = PdfGenerator().lagForsideEttersending(ettersendingsSoknadDto)
		assertTrue(dummyHovedDokument != null)

		// Skriver til tmp fil for manuell sjekk av innholdet av generert PDF
		//writeBytesToFile("dummy", ".pdf", dummyHovedDokument)

	}

	@Test
	fun hentingAvDokumentFeilerNarIngenDokumentOpplastet() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		// Sender inn original soknad
		assertThrows<ResourceNotFoundException> {
			soknadService.hentFil(dokumentSoknadDto, dokumentSoknadDto.vedleggsListe.get(0).id!!, 1L)
		}
	}

	@Test
	fun innsendingFeilerNarIngenDokumentOpplastet() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		// Sender inn original soknad
		assertThrows<IllegalActionException> {
			testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)
		}
	}

	@Test
	fun opprettingAvSoknadVedKallFraFyllUt() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val tema = "HJE"
		val skjemanr = "NAV 10-07.04"
		val innsendingsId = soknadService.opprettNySoknad(lagDokumentSoknad(tema, skjemanr))

		val soknad = soknadService.hentSoknad(innsendingsId)

		assertTrue(soknad.tema == tema &&  soknad.skjemanr == skjemanr)
		assertTrue(soknad.vedleggsListe.size == 2)
		assertTrue(soknad.vedleggsListe.filter { it.erHoveddokument && it.erVariant == false && it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }.isNotEmpty())
	}


	@Test
	fun testAutomatiskSlettingAvGamleSoknader() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val brukerid = testpersonid
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"

		val dokumentSoknadDtoList = mutableListOf<DokumentSoknadDto>()
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak))
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak))

		soknadService.slettGamleIkkeInnsendteSoknader(1L)
		dokumentSoknadDtoList.forEach{ assertEquals(soknadService.hentSoknad(it.id!!).status, SoknadsStatusDto.opprettet) }

		soknadService.slettGamleIkkeInnsendteSoknader(0L)
		dokumentSoknadDtoList.forEach{ assertEquals(soknadService.hentSoknad(it.id!!).status, SoknadsStatusDto.automatiskSlettet) }
	}


	@Test
	fun testAutomatiskSlettingAvFilerTilInnsendteSoknader() {
		val soknadService = SoknadService(skjemaService,	soknadRepository,	vedleggRepository, filRepository,	brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics)

		val brukerid = testpersonid
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"

		val dokumentSoknadDtoList = mutableListOf<DokumentSoknadDto>()
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak, listOf("W1")))
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak, listOf("W1")))

		dokumentSoknadDtoList.forEach {soknadService.lagreFil(it, lagFilDtoMedFil(it.vedleggsListe.filter {it.erHoveddokument}.first()))}
		dokumentSoknadDtoList.forEach {soknadService.lagreFil(it, lagFilDtoMedFil(it.vedleggsListe.filter {!it.erHoveddokument}.first()))}

		val vedleggDtos = slot<List<VedleggDto>>()

		every { fillagerAPI.lagreFiler(any(), capture(vedleggDtos)) } returns Unit

		val soknad = slot<DokumentSoknadDto>()
		val vedleggDtos2 = slot<List<VedleggDto>>()
		every { soknadsmottakerAPI.sendInnSoknad(capture(soknad), capture(vedleggDtos2)) } returns Unit

		val kvitteringsDto = soknadService.sendInnSoknad(dokumentSoknadDtoList[0])
		assertTrue(kvitteringsDto.hoveddokumentRef != null)

		val filDtos = soknadService.hentFiler(dokumentSoknadDtoList[0], dokumentSoknadDtoList[0].innsendingsId!!, dokumentSoknadDtoList[0].vedleggsListe.filter {it.erHoveddokument && !it.erVariant}.first().id!!)
		assertTrue(filDtos.isNotEmpty())

		soknadService.slettfilerTilInnsendteSoknader(-1)
		val innsendtFilDtos = soknadService.hentFiler(dokumentSoknadDtoList[0], dokumentSoknadDtoList[0].innsendingsId!!, dokumentSoknadDtoList[0].vedleggsListe.filter {it.erHoveddokument && !it.erVariant}.first().id!!)
		assertTrue(innsendtFilDtos.isEmpty())

		val ikkeInnsendtfilDtos = soknadService.hentFiler(dokumentSoknadDtoList[1], dokumentSoknadDtoList[1].innsendingsId!!, dokumentSoknadDtoList[1].vedleggsListe.filter {it.erHoveddokument && !it.erVariant}.first().id!!)
		assertTrue(ikkeInnsendtfilDtos.isNotEmpty())

		val kvitteringsDto1 = soknadService.sendInnSoknad(dokumentSoknadDtoList[1])
		assertTrue(kvitteringsDto1.hoveddokumentRef != null)

		soknadService.slettfilerTilInnsendteSoknader(1)
		val filDtos1 = soknadService.hentFiler(dokumentSoknadDtoList[1], dokumentSoknadDtoList[1].innsendingsId!!, dokumentSoknadDtoList[1].vedleggsListe.filter {it.erHoveddokument && !it.erVariant}.first().id!!)
		assertTrue(filDtos1.isNotEmpty())

	}

	private fun lagVedleggDto(skjemanr: String, tittel: String, mimeType: String?, fil: ByteArray?, id: Long? = null,
														erHoveddokument: Boolean? = true, erVariant: Boolean? = false, erPakrevd: Boolean? = true ): VedleggDto {
		return  VedleggDto( tittel, tittel, erHoveddokument!!, erVariant!!,
			if ("application/pdf".equals(mimeType, true)) true else false, erPakrevd!!,
			if (fil != null) OpplastingsStatusDto.lastetOpp else OpplastingsStatusDto.ikkeValgt,  OffsetDateTime.now(), null,
			skjemanr,"Beskrivelse", UUID.randomUUID().toString(),
			if ("application/json".equals(mimeType)) Mimetype.applicationSlashJson else if (mimeType != null) Mimetype.applicationSlashPdf else null,
			fil, null)

	}

	private fun lagDokumentSoknad(brukerId: String, skjemanr: String, spraak: String, tittel: String, tema: String): DokumentSoknadDto {
		val vedleggDtoPdf = lagVedleggDto(skjemanr, tittel, "application/pdf", getBytesFromFile("/litenPdf.pdf"))

		val vedleggDtoJson = lagVedleggDto(skjemanr, tittel, "application/json", getBytesFromFile("/sanity.json"))

		val vedleggDtoList = listOf(vedleggDtoPdf, vedleggDtoJson)
		return DokumentSoknadDto( brukerId, skjemanr, tittel, tema, SoknadsStatusDto.opprettet, OffsetDateTime.now(),
			vedleggDtoList, 1L, UUID.randomUUID().toString(), null, spraak,
			OffsetDateTime.now(), null )
	}

	private fun oppdaterDokumentSoknad(dokumentSoknadDto: DokumentSoknadDto): DokumentSoknadDto {
		val vedleggDto = lastOppDokumentTilVedlegg(dokumentSoknadDto.vedleggsListe[0])
		val vedleggDtoListe = if (dokumentSoknadDto.vedleggsListe.size>1) listOf(dokumentSoknadDto.vedleggsListe[1]) else listOf()
		return DokumentSoknadDto(dokumentSoknadDto.brukerId, dokumentSoknadDto.skjemanr, dokumentSoknadDto.tittel,
			dokumentSoknadDto.tema, SoknadsStatusDto.opprettet, dokumentSoknadDto.opprettetDato, listOf(vedleggDto) + vedleggDtoListe,
			dokumentSoknadDto.id, dokumentSoknadDto.innsendingsId, dokumentSoknadDto.ettersendingsId,
			dokumentSoknadDto.spraak, OffsetDateTime.now(), null )
	}

	private fun lastOppDokumentTilVedlegg(vedleggDto: VedleggDto) =
		lagVedleggDto(vedleggDto.vedleggsnr ?: "N6", vedleggDto.tittel, "application/pdf",
			getBytesFromFile("/litenPdf.pdf"), vedleggDto.id)

	private fun lagFilDtoMedFil(vedleggDto: VedleggDto): FilDto {
		val fil = getBytesFromFile("/litenPdf.pdf")
		return FilDto(
			vedleggDto.id!!, null, "OpplastetFil.pdf",
			Mimetype.applicationSlashPdf, fil.size, fil, OffsetDateTime.now())
	}

	private fun getBytesFromFile(path: String): ByteArray {
		val resourceAsStream = SoknadServiceTest::class.java.getResourceAsStream(path)
		val outputStream = ByteArrayOutputStream()
		resourceAsStream.use { input ->
			outputStream.use { output ->
				input!!.copyTo(output)
			}
		}
		return outputStream.toByteArray()
	}

	// Brukes for å skrive fil til disk for manuell sjekk av innhold.
	private fun writeBytesToFile(navn: String, suffix: String, innhold: ByteArray?) {
		val dest = kotlin.io.path.createTempFile(navn,suffix)

		if (innhold != null)	dest.toFile().writeBytes(innhold)
	}

	private fun lagDokumentSoknad(tema: String, skjemanr: String): DokumentSoknadDto {
		return lagDokumentSoknad(testpersonid, skjemanr, "nb_NO", "Fullmaktsskjema", tema )
	}

	private fun lagVedleggsListe(): List<VedleggDto> {
		// vedleggsliste fra fyllUt vil typisk inneholde hoveddokument (pdf), hoveddokumentvariant (json), vedlegg (påkrevkde) og vedlegg (ikke påkrevde, f.eks. Annet (=N6))
		return listOf(
			lagVedlegg("NAV 10-07.04", "Fullmaktsskjema", "application/pdf",
			true, false, true, no.nav.soknad.innsending.utils.getBytesFromFile("/litenPdf.pdf")
			),
			lagVedlegg("NAV 10-07.04", "Fullmaktsskjema", "application/json",
			true, true, true, no.nav.soknad.innsending.utils.getBytesFromFile("/sanity.json")
			),
			lagVedlegg("N6", "Annen informasjon", null,
				true, true, false, null
			)
		)

	}

	private fun lagVedlegg(vedleggsnr: String, tittel: String, mimeType: String?, erHoveddokument: Boolean,
												 erVariant: Boolean, erPakrevd: Boolean, document: ByteArray?): VedleggDto {
		return lagVedleggDto(vedleggsnr, tittel, mimeType, document, 1, erHoveddokument, erVariant, erPakrevd)

	}
}

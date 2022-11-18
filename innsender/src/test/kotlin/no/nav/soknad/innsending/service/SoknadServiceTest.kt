package no.nav.soknad.innsending.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import io.mockk.slot
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.skjema.SkjemaClient
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.util.Utilities
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
import org.junit.jupiter.api.*
import java.time.LocalDateTime
import java.time.ZoneOffset


@SpringBootTest
@ActiveProfiles("spring")
@EnableTransactionManagement
class SoknadServiceTest {

	@Autowired
	private lateinit var soknadRepository: SoknadRepository

	@Autowired
	private lateinit var vedleggRepository: VedleggRepository

	@Autowired
	private lateinit var filRepository: FilRepository

	@Autowired
	private lateinit var repo: RepositoryUtils

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

	@InjectMockKs
	private val pdlInterface = mockk<PdlInterface>()


	@BeforeEach
	fun setup() {
		every { hentSkjemaData.hent() } returns skjemaService.initSkjemaDataFromDisk()
		every { brukernotifikasjonPublisher.soknadStatusChange(any()) } returns true
		every { pdlInterface.hentPersonData(any()) } returns PersonDto("1234567890", "Kan", null, "Søke")

	}


	@AfterEach
	fun ryddOpp() {
		filRepository.deleteAll()
		vedleggRepository.deleteAll()
		soknadRepository.deleteAll()
	}

	private fun lagSoknadService(): SoknadService = SoknadService(
		skjemaService, repo, brukernotifikasjonPublisher, fillagerAPI,	soknadsmottakerAPI,	innsenderMetrics, pdlInterface)

	@Test
	fun opprettSoknadGittSkjemanr() {
		val soknadService = lagSoknadService()

		val brukerid = testpersonid
		val skjemanr = "NAV 95-00.11"
		val spraak = "nb_NO"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak)
		assertNotNull(dokumentSoknadDto.innsendingsId)
	}

	@Test
	fun opprettSoknadGittSkjemanrOgIkkeStottetSprak() {
		val soknadService = lagSoknadService()

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


	@Test
	fun opprettSoknadGittUkjentSkjemanrKasterException() {
		val soknadService = lagSoknadService()

		val brukerid = testpersonid
		val skjemanr = "NAV XX-00.11"
		val spraak = "nb_NO"
		val exception = assertThrows(
				ResourceNotFoundException::class.java,
				{ soknadService.opprettSoknad(brukerid, skjemanr, spraak) },
				"ResourceNotFoundException was expected")

		assertEquals("Skjema med id = $skjemanr ikke funnet", exception.message)
	}

	@Test
	fun opprettSoknadGittSoknadDokument() {
		val soknadService = lagSoknadService()

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf())

		assertTrue(dokumentSoknadDto != null)
	}


	@Test
	fun opprettSoknadForettersendingAvVedlegg() {
		val soknadService = lagSoknadService()

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "W2"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument }))

		// Sender inn original soknad
		val kvitteringsDto = testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)
		assertTrue(kvitteringsDto.hoveddokumentRef != null )
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty() )
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty() )

		// Opprett ettersendingssoknad
		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.vedleggsListe.none { it.opplastingsStatus == OpplastingsStatusDto.innsendt })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
	}

	@Test
	fun testFlereEttersendingerPaSoknad() {
		val soknadService = lagSoknadService()

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "W2"))

		// Laster opp skjema (hoveddokumentet) til soknaden
		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument }))

		// Sender inn original soknad
		val kvitteringsDto = testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)
		assertTrue(kvitteringsDto.hoveddokumentRef != null )
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty() )
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty() )

		val soknader = soknadService.hentAktiveSoknader(listOf(dokumentSoknadDto.brukerId))
		assertTrue(soknader.isNotEmpty())

		// Oppretter ettersendingssoknad
		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })

		// Laster opp fil til vedlegg W1 til ettersendingssøknaden
		val lagretFil = soknadService.lagreFil(ettersendingsSoknadDto
			, lagFilDtoMedFil(ettersendingsSoknadDto.vedleggsListe.first {
				!it.erHoveddokument && it.vedleggsnr.equals(
					"W1",
					true
				)
			}))

		assertTrue( lagretFil.id != null)
		assertTrue(lagretFil.vedleggsid == ettersendingsSoknadDto.vedleggsListe.first {
			!it.erHoveddokument && it.vedleggsnr.equals(
				"W1",
				true
			)
		}.id)

		val ettersendingsKvitteringsDto = testOgSjekkInnsendingAvSoknad(soknadService, ettersendingsSoknadDto)
		assertTrue(ettersendingsKvitteringsDto.hoveddokumentRef == null )
		assertTrue(ettersendingsKvitteringsDto.innsendteVedlegg!!.isNotEmpty() )
		assertTrue(ettersendingsKvitteringsDto.skalEttersendes!!.isNotEmpty() )

		// Oppretter ettersendingssoknad2
		val ettersendingsSoknadDto2 = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto2.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto2.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertEquals(1, ettersendingsSoknadDto2.vedleggsListe.count { it.opplastingsStatus == OpplastingsStatusDto.innsendt })
		assertTrue(ettersendingsSoknadDto2.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt }.all { it.innsendtdato  != null })
		// Laster opp fil til vedlegg W1 til ettersendingssøknaden
		soknadService.lagreFil(ettersendingsSoknadDto2
			, lagFilDtoMedFil(ettersendingsSoknadDto2.vedleggsListe.first {
				!it.erHoveddokument && it.vedleggsnr.equals(
					"W2",
					true
				)
			}))

		val ettersendingsKvitteringsDto2 = testOgSjekkInnsendingAvSoknad(soknadService, ettersendingsSoknadDto2)
		assertTrue(ettersendingsKvitteringsDto2.hoveddokumentRef == null )
		assertTrue(ettersendingsKvitteringsDto2.innsendteVedlegg!!.isNotEmpty() )
		assertTrue(ettersendingsKvitteringsDto2.skalEttersendes!!.isEmpty() )

		val vedleggDto = soknadService.hentFiler(ettersendingsSoknadDto2, ettersendingsSoknadDto2.innsendingsId!!, ettersendingsSoknadDto2.vedleggsListe.last().id!!, true)
		assertTrue(vedleggDto.isEmpty())
	}



	@Test
	fun opprettSoknadForettersendingAvVedleggGittArkivertSoknadTest_MedUkjentSkjemanr() {

		val soknadService = lagSoknadService()
		val arkiverteVedlegg: List<InnsendtVedleggDto> = listOf(InnsendtVedleggDto(vedleggsnr = "NAV 08-09.10", tittel = "Søknad om å beholde sykepenger under opphold i utlandet"))

		val arkivertSoknad = AktivSakDto("NAV 08-07.04D", "Søknad om Sykepenger",  "SYK",
			LocalDateTime.now().minusDays(10L).atOffset(ZoneOffset.UTC), ettersending = false, innsendingsId = UUID.randomUUID().toString(), innsendtVedleggDtos = arkiverteVedlegg )

		val ettersending = soknadService.opprettSoknadForEttersendingAvVedleggGittArkivertSoknad(brukerId = "1234", arkivertSoknad = arkivertSoknad, "no_NO", listOf("W1") )

		assertTrue(ettersending != null)
		assertEquals(2, ettersending.vedleggsListe.size)

	}

	@Test
	fun hentOpprettetSoknadDokument() {
		val soknadService = lagSoknadService()

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val dokumentSoknadDtoHentet = soknadService.hentSoknad(dokumentSoknadDto.id!!)

		assertEquals(dokumentSoknadDto.id, dokumentSoknadDtoHentet.id)
	}

	@Test
	fun hentIkkeEksisterendeSoknadDokumentKasterFeil() {
		val soknadService = lagSoknadService()

		val exception = assertThrows(
			ResourceNotFoundException::class.java,
			{ soknadService.hentSoknad("9999") },
			"ResourceNotFoundException was expected")

		assertEquals(exception.message, "Ingen soknad med id = 9999 funnet")
	}

	@Test
	fun hentOpprettedeAktiveSoknadsDokument() {
		val soknadService = lagSoknadService()

		testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), testpersonid)
		testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), testpersonid)
		testOgSjekkOpprettingAvSoknad(soknadService, listOf("W2"), "12345678902")
		testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), "12345678903")

		val dokumentSoknadDtos = soknadService.hentAktiveSoknader(listOf(testpersonid, "12345678902"))

		assertTrue(dokumentSoknadDtos.filter { listOf(testpersonid, "12345678902").contains(it.brukerId)}.size == 3)
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
		val arkivertSoknad = AktivSakDto(skjemanr, tittel, tema,
			OffsetDateTime.now().minusDays(2L), false,
			listOf(
				InnsendtVedleggDto(skjemanr,tittel ),
				InnsendtVedleggDto("C1","Vedlegg til $tittel" )),
			arkivertInnsendingsId)

		val soknadService = lagSoknadService()

		val dokumentSoknadDto = soknadService.opprettSoknadForEttersendingAvVedleggGittArkivertSoknad(brukerid, arkivertSoknad, spraak, listOf("C1", "N6", "L8") )

		assertTrue(dokumentSoknadDto.innsendingsId != null  && VisningsType.ettersending == dokumentSoknadDto.visningsType && dokumentSoknadDto.ettersendingsId==arkivertInnsendingsId )
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

		val soknadService = lagSoknadService()
		val dokumentSoknadDto = soknadService.opprettSoknadForEttersendingGittSkjemanr(brukerid, skjemanr,	spraak,	listOf("C1", "L8"))

		assertTrue(dokumentSoknadDto.innsendingsId != null  && VisningsType.ettersending == dokumentSoknadDto.visningsType && dokumentSoknadDto.ettersendingsId==dokumentSoknadDto.innsendingsId )
		assertTrue(dokumentSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(2, dokumentSoknadDto.vedleggsListe.size)
		assertTrue(!dokumentSoknadDto.vedleggsListe.any { it.erHoveddokument && it.vedleggsnr == skjemanr })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "C1" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "L8" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
	}

	@Test
	fun opprettSoknadForEttersendingGittSoknadOgVedleggTest() {

		val soknadService = lagSoknadService()

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "C1", "L8"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument }))
		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.vedleggsnr== "W1" }))

		val kvitteringsDto = testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)
		assertTrue(kvitteringsDto.hoveddokumentRef != null )
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isNotEmpty() )
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty() )

		val innsendtSoknadDto = soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)

		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedleggGittSoknadOgVedlegg(brukerId = testpersonid, nyesteSoknad = innsendtSoknadDto, sprak = "nb_NO", vedleggsnrListe = listOf("N6", "W1"))

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.innsendingsId != null  && VisningsType.ettersending == ettersendingsSoknadDto.visningsType && ettersendingsSoknadDto.ettersendingsId==dokumentSoknadDto.innsendingsId )
		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(4, ettersendingsSoknadDto.vedleggsListe.size)
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.any { it.erHoveddokument && it.vedleggsnr == dokumentSoknadDto.skjemanr })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "N6" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "C1" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "L8" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "W1" && it.opplastingsStatus == OpplastingsStatusDto.innsendt && it.innsendtdato != null})

	}


	@Test
	fun opprettSoknadForEttersendingGittArkivertSoknadOgVedleggTest() {

		val brukerid = testpersonid
		val skjemanr = "NAV 10-07.20"
		val spraak = "nb_NO"
		val arkivertSoknad = AktivSakDto(skjemanr,  "Tittel", "Tema", OffsetDateTime.now(), false, listOf(InnsendtVedleggDto(skjemanr, "Tittel")), Utilities.laginnsendingsId() )
		val opprettEttersendingGittSkjemaNr = OpprettEttersendingGittSkjemaNr(skjemanr, spraak, listOf("C1", "L8"))

		val soknadService = lagSoknadService()
		val dokumentSoknadDto = soknadService.opprettSoknadForettersendingAvVedleggGittArkivertSoknadOgVedlegg(brukerid, arkivertSoknad, opprettEttersendingGittSkjemaNr,	spraak )

		assertTrue(dokumentSoknadDto.innsendingsId != null  && VisningsType.ettersending == dokumentSoknadDto.visningsType && dokumentSoknadDto.ettersendingsId==arkivertSoknad.innsendingsId )
		assertTrue(dokumentSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(2, dokumentSoknadDto.vedleggsListe.size)
		assertTrue(!dokumentSoknadDto.vedleggsListe.any { it.erHoveddokument && it.vedleggsnr == skjemanr })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "C1" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
		assertTrue(dokumentSoknadDto.vedleggsListe.any { !it.erHoveddokument && it.vedleggsnr == "L8" && it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })
	}


	@Test
	fun hentOpprettetVedlegg() {
		val soknadService = lagSoknadService()

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
		val soknadService = lagSoknadService()

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.hentFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns dokumentSoknadDto.vedleggsListe

		val lagretVedleggDto = soknadService.leggTilVedlegg(dokumentSoknadDto, "Litt mer info")

		assertTrue( lagretVedleggDto.id != null && lagretVedleggDto.vedleggsnr == "N6" && lagretVedleggDto.tittel == "Litt mer info" )
	}

	@Test
	fun oppdaterVedleggEndrerKunTittelOgLabel() {
		// Når søker har endret label på et vedlegg av type annet (N6), skal tittel settes lik label og vedlegget i databasen oppdateres med disse endringene.
		val soknadService = lagSoknadService()

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.hentFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns dokumentSoknadDto.vedleggsListe

		val lagretVedleggDto = soknadService.leggTilVedlegg(dokumentSoknadDto, null)
		assertTrue(lagretVedleggDto.id != null && lagretVedleggDto.tittel == "Annet")

		val patchVedleggDto = PatchVedleggDto("Ny tittel", lagretVedleggDto.opplastingsStatus)
		val oppdatertVedleggDto = soknadService.endreVedlegg(patchVedleggDto, lagretVedleggDto.id!!, dokumentSoknadDto)

		assertTrue(oppdatertVedleggDto.id == lagretVedleggDto.id && oppdatertVedleggDto.tittel == "Ny tittel" && oppdatertVedleggDto.vedleggsnr == "N6"
			&& oppdatertVedleggDto.label == oppdatertVedleggDto.tittel)
	}

	@Test
	fun slettOpprettetSoknadDokument() {
		val soknadService = lagSoknadService()

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
		val soknadService = lagSoknadService()

		val vedleggsnr = "N6"
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf(vedleggsnr))

		val lagretVedlegg = dokumentSoknadDto.vedleggsListe.first { e -> vedleggsnr == e.vedleggsnr }

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
		val soknadService = lagSoknadService()

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument }))

		val kvitteringsDto = testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)
		assertTrue(kvitteringsDto.hoveddokumentRef != null )
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty() )
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty() )

		assertThrows<IllegalActionException> {
			soknadService.leggTilVedlegg(dokumentSoknadDto, null)
		}
		soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)

	}

	@Test
	fun sendInnSoknadFeilerUtenOpplastetHoveddokument() {
		val soknadService = lagSoknadService()

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		assertThrows<IllegalActionException> {
			soknadService.sendInnSoknad(dokumentSoknadDto)
		}
		soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)
	}

	@Test
	fun sendInnEttersendingsSoknadFeilerUtenOpplastetVedlegg() {
		val soknadService = lagSoknadService()

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument }))

		val kvitteringsDto = testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)
		assertTrue(kvitteringsDto.hoveddokumentRef != null )
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty() )
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty() )

		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })

		assertThrows<IllegalActionException> {
			soknadService.sendInnSoknad(ettersendingsSoknadDto)
		}
	}


	private fun testOgSjekkOpprettingAvSoknad(soknadService: SoknadService, vedleggsListe: List<String> = listOf(), brukerid: String = testpersonid): DokumentSoknadDto {
		return testOgSjekkOpprettingAvSoknad(soknadService, vedleggsListe, brukerid, "nb_NO" )
	}

	private fun testOgSjekkOpprettingAvSoknad(soknadService: SoknadService, vedleggsListe: List<String> = listOf(), brukerid: String = testpersonid, spraak: String = "nb_NO"): DokumentSoknadDto {
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

	private fun testOgSjekkInnsendingAvSoknad(soknadService: SoknadService, dokumentSoknadDto: DokumentSoknadDto): KvitteringsDto {
		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.lagreFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns Unit

		val soknad = slot<DokumentSoknadDto>()
		val vedleggDtos2 = slot<List<VedleggDto>>()
		every { soknadsmottakerAPI.sendInnSoknad(capture(soknad), capture(vedleggDtos2)) } returns Unit

		val kvitteringsDto = soknadService.sendInnSoknad(dokumentSoknadDto)

		assertTrue(vedleggDtos.isCaptured)
		assertTrue(vedleggDtos.captured.isNotEmpty())

		assertTrue(soknad.isCaptured)
		assertTrue(soknad.captured.innsendingsId == dokumentSoknadDto.innsendingsId)

		assertTrue(kvitteringsDto.innsendingsId == dokumentSoknadDto.innsendingsId )

		return kvitteringsDto
	}

	@Test
	fun lastOppFilTilVedlegg() {
		val soknadService = lagSoknadService()

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = dokumentSoknadDto.vedleggsListe.first { "W1" == it.vedleggsnr }
		assertTrue(vedleggDto != null)

		val filDtoSaved = soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(vedleggDto))

		assertTrue(filDtoSaved != null)
		assertTrue(filDtoSaved.id != null)

		val hentetFilDto = soknadService.hentFil(dokumentSoknadDto, vedleggDto.id!!, filDtoSaved.id!!)
		assertTrue(filDtoSaved.id == hentetFilDto.id)
	}

	@Test
	fun slettFilTilVedlegg() {
		val soknadService = lagSoknadService()

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = dokumentSoknadDto.vedleggsListe.first { "W1" == it.vedleggsnr }
		assertTrue(vedleggDto != null)

		val filDtoSaved = soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(vedleggDto))

		assertTrue(filDtoSaved != null)
		assertTrue(filDtoSaved.id != null)

		val oppdatertSoknadDto = soknadService.hentSoknad(dokumentSoknadDto.id!!)
		assertEquals(OpplastingsStatusDto.lastetOpp, oppdatertSoknadDto.vedleggsListe.first { it.id == vedleggDto.id!! }.opplastingsStatus)

		val oppdatertVedleggDto = soknadService.slettFil(oppdatertSoknadDto, filDtoSaved.vedleggsid, filDtoSaved.id!!)

		assertEquals(OpplastingsStatusDto.ikkeValgt, oppdatertVedleggDto.opplastingsStatus)
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
		val soknadService = lagSoknadService()

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument }))

		// Sender inn original soknad
		val kvitteringsDto = testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)
		assertTrue(kvitteringsDto.hoveddokumentRef != null )
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty() )
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty() )


		// Test generering av kvittering for innsendt soknad.
		// Merk det er besluttet og ikke sende kvittering med innsendingen av søknaden. Det innebærer at denne koden pt er redundant
		val innsendtSoknad = soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)
		val kvitteringsDokument = PdfGenerator().lagKvitteringsSide(innsendtSoknad, "Per Person",
			innsendtSoknad.vedleggsListe.filter { it.opplastingsStatus==OpplastingsStatusDto.innsendt },
			innsendtSoknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.sendSenere })
		assertTrue(kvitteringsDokument != null)

		// Skriver til tmp fil for manuell sjekk av innholdet av generert PDF
		writeBytesToFile("dummy", ".pdf", kvitteringsDokument)

	}


	@Test
	fun lagDummyHoveddokumentForEttersending() {
		val soknadService = lagSoknadService()

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument }))

		// Sender inn original soknad
		val kvitteringsDto = testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)
		assertTrue(kvitteringsDto.hoveddokumentRef != null )
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty() )
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty() )

		// Opprett ettersendingssoknad
		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.vedleggsListe.none { it.opplastingsStatus == OpplastingsStatusDto.innsendt })
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })

		val dummyHovedDokument = PdfGenerator().lagForsideEttersending(ettersendingsSoknadDto)
		assertTrue(dummyHovedDokument != null)

		// Skriver til tmp fil for manuell sjekk av innholdet av generert PDF
		//writeBytesToFile("dummy", ".pdf", dummyHovedDokument)
	}

	@Test
	fun hentingAvDokumentFeilerNarIngenDokumentOpplastet() {
		val soknadService = lagSoknadService()

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		// Sender inn original soknad
		assertThrows<ResourceNotFoundException> {
			soknadService.hentFil(dokumentSoknadDto, dokumentSoknadDto.vedleggsListe[0].id!!, 1L)
		}
	}

	@Test
	fun innsendingFeilerNarIngenDokumentOpplastet() {
		val soknadService = lagSoknadService()

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		// Sender inn original soknad
		assertThrows<IllegalActionException> {
			testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)
		}
	}

	@Test
	fun opprettingAvSoknadVedKallFraFyllUt() {
		val soknadService = lagSoknadService()

		val tema = "HJE"
		val skjemanr = "NAV 10-07.04"
		val innsendingsId = soknadService.opprettNySoknad(lagDokumentSoknad(tema, skjemanr))

		val soknad = soknadService.hentSoknad(innsendingsId)

		assertTrue(soknad.tema == tema &&  soknad.skjemanr == skjemanr)
		assertTrue(soknad.vedleggsListe.size == 2)
		assertTrue(soknad.vedleggsListe.any { it.erHoveddokument && !it.erVariant && it.opplastingsStatus == OpplastingsStatusDto.lastetOpp })
	}


	@Test
	fun testAutomatiskSlettingAvGamleSoknader() {
		val soknadService = lagSoknadService()

		val brukerid = testpersonid
		val skjemanr = "NAV 95-00.11"
		val spraak = "nb_NO"

		val dokumentSoknadDtoList = mutableListOf<DokumentSoknadDto>()
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak))
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak))

		soknadService.slettGamleSoknader(1L)
		dokumentSoknadDtoList.forEach{ assertEquals(soknadService.hentSoknad(it.id!!).status, SoknadsStatusDto.opprettet) }

		soknadService.slettGamleSoknader(0L)
		dokumentSoknadDtoList.forEach{ assertEquals(soknadService.hentSoknad(it.id!!).status, SoknadsStatusDto.automatiskSlettet) }
	}


	@Test
	fun testAutomatiskSlettingAvFilerTilInnsendteSoknader() {
		val soknadService = lagSoknadService()

		val brukerid = testpersonid
		val skjemanr = "NAV 95-00.11"
		val spraak = "nb_NO"

		val dokumentSoknadDtoList = mutableListOf<DokumentSoknadDto>()
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak, listOf("W1")))
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak, listOf("W1")))

		dokumentSoknadDtoList.forEach { it -> soknadService.lagreFil(it, lagFilDtoMedFil(it.vedleggsListe.first { it.erHoveddokument }))}
		dokumentSoknadDtoList.forEach { it -> soknadService.lagreFil(it, lagFilDtoMedFil(it.vedleggsListe.first { !it.erHoveddokument }))}

		val vedleggDtos = slot<List<VedleggDto>>()

		every { fillagerAPI.lagreFiler(any(), capture(vedleggDtos)) } returns Unit

		val soknad = slot<DokumentSoknadDto>()
		val vedleggDtos2 = slot<List<VedleggDto>>()
		every { soknadsmottakerAPI.sendInnSoknad(capture(soknad), capture(vedleggDtos2)) } returns Unit

		val kvitteringsDto = soknadService.sendInnSoknad(dokumentSoknadDtoList[0])
		assertTrue(kvitteringsDto.hoveddokumentRef != null)

		val filDtos = soknadService.hentFiler(dokumentSoknadDtoList[0], dokumentSoknadDtoList[0].innsendingsId!!,
			dokumentSoknadDtoList[0].vedleggsListe.first { it.erHoveddokument && !it.erVariant }.id!!)
		assertTrue(filDtos.isNotEmpty())

		soknadService.slettfilerTilInnsendteSoknader(-1)
		val innsendtFilDtos = soknadService.hentFiler(dokumentSoknadDtoList[0], dokumentSoknadDtoList[0].innsendingsId!!,
			dokumentSoknadDtoList[0].vedleggsListe.first { it.erHoveddokument && !it.erVariant }.id!!)
		assertTrue(innsendtFilDtos.isEmpty())

		val ikkeInnsendtfilDtos = soknadService.hentFiler(dokumentSoknadDtoList[1], dokumentSoknadDtoList[1].innsendingsId!!,
			dokumentSoknadDtoList[1].vedleggsListe.first { it.erHoveddokument && !it.erVariant }.id!!)
		assertTrue(ikkeInnsendtfilDtos.isNotEmpty())

		val kvitteringsDto1 = soknadService.sendInnSoknad(dokumentSoknadDtoList[1])
		assertTrue(kvitteringsDto1.hoveddokumentRef != null)

		soknadService.slettfilerTilInnsendteSoknader(1)
		val filDtos1 = soknadService.hentFiler(dokumentSoknadDtoList[1], dokumentSoknadDtoList[1].innsendingsId!!,
			dokumentSoknadDtoList[1].vedleggsListe.first { it.erHoveddokument && !it.erVariant }.id!!)
		assertTrue(filDtos1.isNotEmpty())

	}

	private fun lagVedleggDto(skjemanr: String, tittel: String, mimeType: String?, fil: ByteArray?, id: Long? = null,
														erHoveddokument: Boolean? = true, erVariant: Boolean? = false, erPakrevd: Boolean? = true ): VedleggDto {
		return  VedleggDto( tittel, tittel, erHoveddokument!!, erVariant!!,
			"application/pdf".equals(mimeType, true), erPakrevd!!,
			if (fil != null) OpplastingsStatusDto.lastetOpp else OpplastingsStatusDto.ikkeValgt,  OffsetDateTime.now(), id,
			skjemanr,"Beskrivelse", UUID.randomUUID().toString(),
			if ("application/json" == mimeType) Mimetype.applicationSlashJson else if (mimeType != null) Mimetype.applicationSlashPdf else null,
			fil, null)
	}

	private fun lagDokumentSoknad(brukerId: String, skjemanr: String, spraak: String, tittel: String, tema: String): DokumentSoknadDto {
		val vedleggDtoPdf = lagVedleggDto(skjemanr, tittel, "application/pdf", getBytesFromFile("/litenPdf.pdf"))

		val vedleggDtoJson = lagVedleggDto(skjemanr, tittel, "application/json", getBytesFromFile("/sanity.json"))

		val vedleggDtoList = listOf(vedleggDtoPdf, vedleggDtoJson)
		return DokumentSoknadDto( brukerId, skjemanr, tittel, tema, SoknadsStatusDto.opprettet, OffsetDateTime.now(),
			vedleggDtoList, 1L, UUID.randomUUID().toString(), null, spraak,
			OffsetDateTime.now(), null, 0, VisningsType.fyllUt )
	}

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

//		if (innhold != null)	dest.toFile().writeBytes(innhold)
	}


	private fun lagDokumentSoknad(tema: String, skjemanr: String): DokumentSoknadDto {
		return lagDokumentSoknad(testpersonid, skjemanr, "nb_NO", "Fullmaktsskjema", tema )
	}
}

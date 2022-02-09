package no.nav.soknad.innsending.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import io.mockk.slot
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.skjema.SkjemaClient
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerAPI
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPI
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.FilDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.pdfutilities.PdfGenerator
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.*

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

	@InjectMockKs
	private val brukernotifikasjonPublisher = mockk<BrukernotifikasjonPublisher>()

	@InjectMockKs
	private val hentSkjemaData = mockk<SkjemaClient>()

	@InjectMockKs
	private val fillagerAPI = mockk<FillagerAPI>()

	@InjectMockKs
	private val soknadsmottakerAPI = mockk<MottakerAPI>()

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
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val brukerid = "12345678901"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak)
		assertNotNull(dokumentSoknadDto.innsendingsId)
	}

	@Test
	fun opprettSoknadGittSoknadDokument() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf())

		assertTrue(dokumentSoknadDto != null)
	}


	@Test
	fun opprettSoknadForettersendingAvVedlegg() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		// Sender inn original soknad
		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

		// Opprett ettersendingssoknad
		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto != null)
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.isEmpty())
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatus.INNSENDT }.toList().isEmpty())
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatus.IKKE_VALGT }.toList().isEmpty())

	}

	@Test
	fun testFlereEttersendingerPaSoknad() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "W2"))

		// Laster opp skjema (hoveddokumentet) til soknaden
		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		// Sender inn original soknad
		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

		// Oppretter ettersendingssoknad
		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(!ettersendingsSoknadDto.vedleggsListe.isEmpty())
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatus.IKKE_VALGT }.toList().isEmpty())

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
		assertTrue(!ettersendingsSoknadDto2.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatus.IKKE_VALGT }.toList().isEmpty())
		assertTrue(ettersendingsSoknadDto2.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatus.INNSENDT }.toList().count() == 2 )

		// Laster opp fil til vedlegg W1 til ettersendingssøknaden
		soknadService.lagreFil(ettersendingsSoknadDto2
			, lagFilDtoMedFil(ettersendingsSoknadDto2.vedleggsListe.filter {!it.erHoveddokument && it.vedleggsnr.equals("W2", true)}.first()))

		testOgSjekkInnsendingAvSoknad(soknadService, ettersendingsSoknadDto2)
	}


	@Test
	fun hentOpprettetSoknadDokument() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val dokumentSoknadDtoHentet = soknadService.hentSoknad(dokumentSoknadDto.id!!)

		assertEquals(dokumentSoknadDto.id, dokumentSoknadDtoHentet.id)
	}

	@Test
	fun hentOpprettedeAktiveSoknadsDokument() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), "12345678901")
		testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), "12345678901")
		testOgSjekkOpprettingAvSoknad(soknadService, listOf("W2"), "12345678902")
		testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), "12345678903")

		val dokumentSoknadDtos = soknadService.hentAktiveSoknader(listOf("12345678901", "12345678902"))

		assertTrue(dokumentSoknadDtos.filter { listOf("12345678901", "12345678902").contains(it.brukerId)}.size == 3)
		assertTrue(dokumentSoknadDtos.filter { listOf("12345678903").contains(it.brukerId)}.size == 0)

	}


	@Test
	fun hentOpprettetVedlegg() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.hentFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns dokumentSoknadDto.vedleggsListe

		val vedleggDto = soknadService.hentVedleggDto(dokumentSoknadDto.vedleggsListe[0].id!!)

		assertEquals(vedleggDto.id, dokumentSoknadDto.vedleggsListe[0].id!!)
	}

	@Test
	fun leggTilVedlegg() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.hentFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns dokumentSoknadDto.vedleggsListe

		val vedleggDto = lagVedleggDto("N6", "Annet", null, null)
		val lagretVedleggDto = soknadService.lagreVedlegg(vedleggDto,dokumentSoknadDto.innsendingsId!!)

		assertTrue(lagretVedleggDto != null && lagretVedleggDto.id != null)
	}

	@Test
	fun slettOpprettetSoknadDokument() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

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
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val vedleggsnr = "N6"
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf(vedleggsnr))

		val lagretVedlegg = dokumentSoknadDto.vedleggsListe.first { e -> vedleggsnr.equals(e.vedleggsnr) }

		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.slettFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns Unit

		soknadService.slettVedlegg(dokumentSoknadDto, lagretVedlegg.id!!, )

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
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

		assertThrows<IllegalActionException> {
			soknadService.lagreVedlegg(lagVedleggDto("W2", "Nytt vedlegg", null, null), dokumentSoknadDto.innsendingsId!!)
		}
		soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)

	}

	private fun testOgSjekkOpprettingAvSoknad(soknadService: SoknadService, vedleggsListe: List<String> = listOf(), brukerid: String = "12345678901"): DokumentSoknadDto {
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak, vedleggsListe)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak)
		assertTrue(dokumentSoknadDto.innsendingsId != null)
		assertTrue(dokumentSoknadDto.vedleggsListe.size == vedleggsListe.size+1)

		return dokumentSoknadDto

	}

	private fun testOgSjekkOpprettingAvSoknad(soknadService: SoknadService, vedleggsListe: List<String> = listOf()): DokumentSoknadDto {
		return testOgSjekkOpprettingAvSoknad(soknadService, vedleggsListe, "12345678901")
	}

	private fun testOgSjekkInnsendingAvSoknad(soknadService: SoknadService, dokumentSoknadDto: DokumentSoknadDto) {
		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.lagreFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns Unit

		val soknad = slot<DokumentSoknadDto>()
		every { soknadsmottakerAPI.sendInnSoknad(capture(soknad)) } returns Unit

		soknadService.sendInnSoknad(dokumentSoknadDto)

		assertTrue(vedleggDtos.isCaptured)
		assertTrue(vedleggDtos.captured.size >= 1)

		assertTrue(soknad.isCaptured)
		assertTrue(soknad.captured.innsendingsId == dokumentSoknadDto.innsendingsId)

	}

	@Test
	fun lastOppFilTilVedlegg() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

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
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = dokumentSoknadDto.vedleggsListe.filter { "W1".equals(it.vedleggsnr) }.first()
		assertTrue(vedleggDto != null)

		val filDtoSaved = soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(vedleggDto))

		assertTrue(filDtoSaved != null)
		assertTrue(filDtoSaved.id != null)

		soknadService.slettFil(dokumentSoknadDto, filDtoSaved.vedleggsid, filDtoSaved.id!!)

	}

	private fun lagVedleggDto(skjemanr: String, tittel: String, mimeType: String?, fil: ByteArray?): VedleggDto {
		return  VedleggDto(null, skjemanr, tittel, UUID.randomUUID().toString(), mimeType, fil
			, false, erVariant = false, if ("application/pdf".equals(mimeType, true)) true else false, null, OpplastingsStatus.IKKE_VALGT,  LocalDateTime.now())

	}
	private fun lagDokumentSoknad(brukerId: String, skjemanr: String, spraak: String, tittel: String, tema: String): DokumentSoknadDto {
			val vedleggDtoPdf = VedleggDto(null, skjemanr, tittel, UUID.randomUUID().toString(), "application/pdf",
					getBytesFromFile("/litenPdf.pdf"), true, erVariant = false, true, null, OpplastingsStatus.LASTET_OPP,  LocalDateTime.now())
			val vedleggDtoJson = VedleggDto(null, skjemanr, tittel, UUID.randomUUID().toString(),"application/json",
					getBytesFromFile("/sanity.json"), true, erVariant = true, false, null, OpplastingsStatus.LASTET_OPP, LocalDateTime.now())

		val vedleggDtoList = listOf(vedleggDtoPdf, vedleggDtoJson)
		return DokumentSoknadDto(null, null, null, brukerId, skjemanr, tittel, tema, spraak,
			SoknadsStatus.Opprettet, LocalDateTime.now(), LocalDateTime.now(), null, vedleggDtoList)
	}

	private fun oppdaterDokumentSoknad(dokumentSoknadDto: DokumentSoknadDto): DokumentSoknadDto {
		val vedleggDto = lastOppDokumentTilVedlegg(dokumentSoknadDto.vedleggsListe[0])
		val vedleggDtoListe = if (dokumentSoknadDto.vedleggsListe.size>1) listOf(dokumentSoknadDto.vedleggsListe[1]) else listOf()
		return DokumentSoknadDto(dokumentSoknadDto.id, dokumentSoknadDto.innsendingsId, dokumentSoknadDto.ettersendingsId,
			dokumentSoknadDto.brukerId, dokumentSoknadDto.skjemanr, dokumentSoknadDto.tittel, dokumentSoknadDto.tema,
			dokumentSoknadDto.spraak, SoknadsStatus.Opprettet, dokumentSoknadDto.opprettetDato, LocalDateTime.now(),
			null, listOf(vedleggDto) + vedleggDtoListe)
	}

	private fun lastOppDokumentTilVedlegg(vedleggDto: VedleggDto) =
		VedleggDto(vedleggDto.id, vedleggDto.vedleggsnr, vedleggDto.tittel, UUID.randomUUID().toString(),
			"application/pdf", getBytesFromFile("/litenPdf.pdf"), true, erVariant = false,
			true, vedleggDto.skjemaurl, OpplastingsStatus.LASTET_OPP, LocalDateTime.now())

	private fun lagFilDtoMedFil(vedleggDto: VedleggDto): FilDto {
		val fil = getBytesFromFile("/litenPdf.pdf")
		return FilDto(
			null, vedleggDto.id!!, "Opplastet fil",
			"application/pdf", fil.size, fil, LocalDateTime.now()
		)
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
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

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
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		// Sender inn original soknad
		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

		// Opprett ettersendingssoknad
		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		assertTrue(ettersendingsSoknadDto != null)
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.isEmpty())
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatus.INNSENDT }.toList().isEmpty())
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatus.IKKE_VALGT }.toList().isEmpty())

		val dummyHovedDokument = PdfGenerator().lagForsideEttersending(ettersendingsSoknadDto)
		assertTrue(dummyHovedDokument != null)

		// Skriver til tmp fil for manuell sjekk av innholdet av generert PDF
		//writeBytesToFile("dummy", ".pdf", dummyHovedDokument)

	}

	@Test
	fun hentingAvDokumentFeilerNarIngenDokumentOpplastet() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		// Sender inn original soknad
		assertThrows<ResourceNotFoundException> {
			soknadService.hentFil(dokumentSoknadDto, dokumentSoknadDto.vedleggsListe.get(0).id!!, 1L)
		}
	}

	@Test
	fun innsendingFeilerNarIngenDokumentOpplastet() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		// Opprett original soknad
		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		// Sender inn original soknad
		assertThrows<IllegalActionException> {
			testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)
		}
	}

}

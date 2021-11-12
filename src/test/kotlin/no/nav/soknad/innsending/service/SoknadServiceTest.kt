package no.nav.soknad.innsending.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import io.mockk.slot
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.skjema.SkjemaClient
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerAPI
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.SoknadsmottakerAPI
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.FilDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.*

@SpringBootTest
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
	private val soknadsmottakerAPI = mockk<SoknadsmottakerAPI>()

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

		soknadService.lagreFil(dokumentSoknadDto.innsendingsId!!, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		// Sender inn original soknad
		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

		// Opprett ettersendingssoknad
		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!, dokumentSoknadDto.spraak!!)

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
		soknadService.lagreFil(dokumentSoknadDto.innsendingsId!!, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		// Sender inn original soknad
		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

		// Oppretter ettersendingssoknad
		val ettersendingsSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!, dokumentSoknadDto.spraak!!)

		assertTrue(ettersendingsSoknadDto != null)
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.isEmpty())
		assertTrue(!ettersendingsSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatus.IKKE_VALGT }.toList().isEmpty())

		// Laster opp fil til vedlegg W1 til ettersendingssøknaden
		val lagretFil = soknadService.lagreFil(ettersendingsSoknadDto.innsendingsId!!
			, lagFilDtoMedFil(ettersendingsSoknadDto.vedleggsListe.filter {!it.erHoveddokument && it.vedleggsnr.equals("W1", true)}.first()))

		assertTrue(lagretFil != null && lagretFil.id != null && lagretFil.data != null)
		assertTrue(lagretFil.vedleggsid == ettersendingsSoknadDto.vedleggsListe.filter {!it.erHoveddokument && it.vedleggsnr.equals("W1", true)}.first().id)

		testOgSjekkInnsendingAvSoknad(soknadService, ettersendingsSoknadDto)

		// Oppretter ettersendingssoknad2
		val ettersendingsSoknadDto2 = soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!, dokumentSoknadDto.spraak!!)

		assertTrue(ettersendingsSoknadDto2 != null)
		assertTrue(!ettersendingsSoknadDto2.vedleggsListe.isEmpty())
		assertTrue(!ettersendingsSoknadDto2.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatus.IKKE_VALGT }.toList().isEmpty())
		assertTrue(ettersendingsSoknadDto2.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatus.INNSENDT }.toList().count() == 2 )

		// Laster opp fil til vedlegg W1 til ettersendingssøknaden
		soknadService.lagreFil(ettersendingsSoknadDto2.innsendingsId!!
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
	fun hentOpprettetVedlegg() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.hentFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns dokumentSoknadDto.vedleggsListe

		val vedleggDto = soknadService.hentVedlegg(dokumentSoknadDto.vedleggsListe[0].id!!)

		assertEquals(vedleggDto.id, dokumentSoknadDto.vedleggsListe[0].id!!)
	}

	@Test
	fun leggTilVedlegg() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.hentFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns dokumentSoknadDto.vedleggsListe

		val vedleggDto = lagVedleggDto("N6", "Annet", null, null)
		val lagretVedleggDto = soknadService.lagreVedlegg(vedleggDto,dokumentSoknadDto.id!!)

		assertTrue(lagretVedleggDto != null && lagretVedleggDto.id != null)
	}

	@Test
	fun slettOpprettetSoknadDokument() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf())

		val slett = slot<List<VedleggDto>>()
		every { fillagerAPI.slettFiler(any(), capture(slett)) } returns Unit

		soknadService.slettSoknadAvBruker(dokumentSoknadDto.innsendingsId!!)

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

		soknadService.slettVedleggOgDensFiler(lagretVedlegg, dokumentSoknadDto.id!!)

/* Sender ikke opplastede filer fortløpende til soknadsfillager
		assertTrue(vedleggDtos.isCaptured)
		assertTrue(vedleggDtos.captured.get(0).id == dokumentSoknadDto.vedleggsListe[1].id)
*/

		assertThrows<Exception> {
			soknadService.hentVedlegg(lagretVedlegg.id!!)
		}
	}


	@Test
	fun sendInnSoknad() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		soknadService.lagreFil(dokumentSoknadDto.innsendingsId!!, lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.filter {it.erHoveddokument}.first()))

		testOgSjekkInnsendingAvSoknad(soknadService, dokumentSoknadDto)

	}

	private fun testOgSjekkOpprettingAvSoknad(soknadService: SoknadService, vedleggsListe: List<String> = listOf()): DokumentSoknadDto {
		val brukerid = "12345678901"
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

	private fun testOgSjekkInnsendingAvSoknad(soknadService: SoknadService, dokumentSoknadDto: DokumentSoknadDto) {
		val vedleggDtos = slot<List<VedleggDto>>()
		every { fillagerAPI.lagreFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns Unit

		val soknad = slot<DokumentSoknadDto>()
		every { soknadsmottakerAPI.sendInnSoknad(capture(soknad)) } returns Unit

		soknadService.sendInnSoknad(dokumentSoknadDto.innsendingsId!!)

		assertTrue(vedleggDtos.isCaptured)
		assertTrue(vedleggDtos.captured.size == 1)

		assertTrue(soknad.isCaptured)
		assertTrue(soknad.captured.innsendingsId == dokumentSoknadDto.innsendingsId)

	}

	@Test
	fun lastOppFilTilVedlegg() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = dokumentSoknadDto.vedleggsListe.filter { "W1".equals(it.vedleggsnr) }.first()
		assertTrue(vedleggDto != null)

		val filDtoSaved = soknadService.lagreFil(dokumentSoknadDto.innsendingsId!!, lagFilDtoMedFil(vedleggDto))

		assertTrue(filDtoSaved != null)
		assertTrue(filDtoSaved.id != null)

	}

	@Test
	fun slettFilTilVedlegg() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository, filRepository, brukernotifikasjonPublisher, fillagerAPI, soknadsmottakerAPI )

		val dokumentSoknadDto = testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = dokumentSoknadDto.vedleggsListe.filter { "W1".equals(it.vedleggsnr) }.first()
		assertTrue(vedleggDto != null)

		val filDtoSaved = soknadService.lagreFil(dokumentSoknadDto.innsendingsId!!, lagFilDtoMedFil(vedleggDto))

		assertTrue(filDtoSaved != null)
		assertTrue(filDtoSaved.id != null)

		soknadService.slettFil(dokumentSoknadDto.innsendingsId!!, filDtoSaved.vedleggsid, filDtoSaved.id!!)

	}

	private fun lagVedleggDto(skjemanr: String, tittel: String, mimeType: String?, fil: ByteArray?): VedleggDto {
		return  VedleggDto(null, skjemanr, tittel, UUID.randomUUID().toString(), mimeType, fil
			, false, erVariant = false, if ("application/pdf".equals(mimeType, true)) true else false, OpplastingsStatus.IKKE_VALGT,  LocalDateTime.now())

	}
	private fun lagDokumentSoknad(brukerId: String, skjemanr: String, spraak: String, tittel: String, tema: String): DokumentSoknadDto {
			val vedleggDtoPdf = VedleggDto(null, skjemanr, tittel, UUID.randomUUID().toString(), "application/pdf",
					getBytesFromFile("/litenPdf.pdf"), true, erVariant = false, true, OpplastingsStatus.LASTET_OPP,  LocalDateTime.now())
			val vedleggDtoJson = VedleggDto(null, skjemanr, tittel, UUID.randomUUID().toString(),"application/json",
					getBytesFromFile("/sanity.json"), true, erVariant = true, false, OpplastingsStatus.LASTET_OPP, LocalDateTime.now())

		val vedleggDtoList = listOf(vedleggDtoPdf, vedleggDtoJson)
		return DokumentSoknadDto(null, null, null, brukerId, skjemanr, tittel, tema, spraak, null,
			SoknadsStatus.Opprettet, LocalDateTime.now(), LocalDateTime.now(), null, vedleggDtoList)
	}

	private fun oppdaterDokumentSoknad(dokumentSoknadDto: DokumentSoknadDto): DokumentSoknadDto {
		val vedleggDto = lastOppDokumentTilVedlegg(dokumentSoknadDto.vedleggsListe[0])
		val vedleggDtoListe = if (dokumentSoknadDto.vedleggsListe.size>1) listOf(dokumentSoknadDto.vedleggsListe[1]) else listOf()
		return DokumentSoknadDto(dokumentSoknadDto.id, dokumentSoknadDto.innsendingsId, dokumentSoknadDto.ettersendingsId,
			dokumentSoknadDto.brukerId, dokumentSoknadDto.skjemanr, dokumentSoknadDto.tittel, dokumentSoknadDto.tema,
			dokumentSoknadDto.spraak, dokumentSoknadDto.skjemaurl, SoknadsStatus.Opprettet, dokumentSoknadDto.opprettetDato, LocalDateTime.now(),
			null, listOf(vedleggDto) + vedleggDtoListe)
	}

	private fun lastOppDokumentTilVedlegg(vedleggDto: VedleggDto) =
		VedleggDto(vedleggDto.id, vedleggDto.vedleggsnr, vedleggDto.tittel, UUID.randomUUID().toString(),
			"application/pdf", getBytesFromFile("/litenPdf.pdf"), true, erVariant = false,
			true, OpplastingsStatus.LASTET_OPP, LocalDateTime.now())

	private fun lagFilDtoMedFil(vedleggDto: VedleggDto) =
		FilDto(null, vedleggDto.id!!, "Opplastet fil",
			"application/pdf", getBytesFromFile("/litenPdf.pdf"),  LocalDateTime.now())

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
}

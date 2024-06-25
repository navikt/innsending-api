package no.nav.soknad.innsending.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.PatchVedleggDto
import no.nav.soknad.innsending.model.SoknadFile
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.SoknadAssertions
import no.nav.soknad.innsending.utils.builders.ettersending.InnsendtVedleggDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.OpprettEttersendingTestBuilder
import no.nav.soknad.pdfutilities.AntallSider
import no.nav.soknad.pdfutilities.PdfGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertNotNull

class InnsendingServiceTest : ApplicationTest() {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	lateinit var soknadService: SoknadService

	@Autowired
	private lateinit var filService: FilService

	@Autowired
	private lateinit var restConfig: RestConfig

	@Autowired
	private lateinit var vedleggService: VedleggService

	@Autowired
	private lateinit var tilleggstonadService: TilleggsstonadService

	@Autowired
	private lateinit var ettersendingService: EttersendingService

	@Autowired
	private lateinit var exceptionHelper: ExceptionHelper

	val soknadsmottakerAPI = mockk<MottakerInterface>()
	private val brukernotifikasjonPublisher = mockk<BrukernotifikasjonPublisher>()
	private val pdlInterface = mockk<PdlInterface>()

	@MockkBean
	private lateinit var subjectHandler: SubjectHandlerInterface

	fun lagInnsendingService(soknadService: SoknadService): InnsendingService = InnsendingService(
		soknadService = soknadService,
		repo = repo,
		vedleggService = vedleggService,
		tilleggstonadService = tilleggstonadService,
		ettersendingService = ettersendingService,
		filService = filService,
		brukernotifikasjonPublisher = brukernotifikasjonPublisher,
		innsenderMetrics = innsenderMetrics,
		exceptionHelper = exceptionHelper,
		soknadsmottakerAPI = soknadsmottakerAPI,
		restConfig = restConfig,
		pdlInterface = pdlInterface,
	)

	@BeforeEach
	fun setup() {
		every { brukernotifikasjonPublisher.soknadStatusChange(any()) } returns true
		every { pdlInterface.hentPersonData(any()) } returns PersonDto("1234567890", "Kan", null, "Søke")
		every { subjectHandler.getClientId() } returns "application"
	}


	@Test
	fun sendInnSoknad() {
		val innsendingService = lagInnsendingService(soknadService)
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument })
		)

		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)

		assertTrue(kvitteringsDto.hoveddokumentRef != null)
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty())

		assertThrows<IllegalActionException> {
			vedleggService.leggTilVedlegg(dokumentSoknadDto, null)
		}
		soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)

		// Hvis hent innsendt hoveddokument
		val hoveddok = innsendingService.getFiles(
			dokumentSoknadDto.innsendingsId!!,
			dokumentSoknadDto.vedleggsListe.filter { it.erHoveddokument }.map { it.uuid!! }.toList()
		)

		// Så skal
		assertEquals(1, hoveddok.size)
		assertTrue(hoveddok.all { it.fileStatus == SoknadFile.FileStatus.ok })

	}

	@Test
	fun sendInnSoknadFeilerUtenOpplastetHoveddokument() {
		val innsendingService = lagInnsendingService(soknadService)

		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		assertThrows<IllegalActionException> {
			innsendingService.sendInnSoknad(dokumentSoknadDto)
		}
		soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)
	}

	@Test
	fun sendInnEttersendingsSoknadFeilerUtenOpplastetVedlegg() {
		val innsendingService = lagInnsendingService(soknadService)


		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument })
		)

		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty())

		val ettersending = OpprettEttersendingTestBuilder()
			.skjemanr(dokumentSoknadDto.skjemanr)
			.vedleggsListe(
				listOf(
					InnsendtVedleggDtoTestBuilder().vedleggsnr("W1").tittel("Vedlegg1").build(),
				)
			)
			.build()

		val ettersendingsSoknadDto =
			ettersendingService.createEttersendingFromExistingSoknader(dokumentSoknadDto.brukerId, ettersending)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt })
		assertEquals(1, ettersendingsSoknadDto.vedleggsListe.size)

		assertThrows<IllegalActionException> {
			innsendingService.sendInnSoknad(ettersendingsSoknadDto)
		}
	}

	@Test
	fun lagKvitteringsHoveddokument() {
		val innsendingService = lagInnsendingService(soknadService)

		// Opprett original soknad
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "X1", "X2"))

		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument })
		)

		vedleggService.endreVedlegg(
			patchVedleggDto = PatchVedleggDto(
				tittel = null,
				opplastingsStatus = OpplastingsStatusDto.NavKanHenteDokumentasjon,
				opplastingsValgKommentar = "NAV kan innhente inntektsopplysninger for meg fra skatt"
			),
			vedleggsId = dokumentSoknadDto.vedleggsListe.first { it.vedleggsnr == "X1" }.id!!,
			soknadDto = dokumentSoknadDto, required = true
		)

		vedleggService.endreVedlegg(
			patchVedleggDto = PatchVedleggDto(
				tittel = null,
				opplastingsStatus = OpplastingsStatusDto.NavKanHenteDokumentasjon,
				opplastingsValgKommentarLedetekst = "Bekreft at NAV kan innente denne informasjonen",
				opplastingsValgKommentar = "Bekrefter at NAV kan innhente informasjon om Dokumentasjon av sosialhjelp"
			),
			vedleggsId = dokumentSoknadDto.vedleggsListe.first { it.vedleggsnr == "X2" }.id!!,
			soknadDto = dokumentSoknadDto, required = true
		)

		val oppdatertDokumentDto = soknadService.hentSoknad(dokumentSoknadDto.id!!)

		// Sender inn original soknad
		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				oppdatertDokumentDto,
				innsendingService
			)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty())


		// Test generering av kvittering for innsendt soknad.
		// Merk det er besluttet og ikke sende kvittering med innsendingen av søknaden. Det innebærer at denne koden pt er redundant
		val innsendtSoknad = soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)
		val kvitteringsDokument = PdfGenerator().lagKvitteringsSide(innsendtSoknad, "Per Person",
			innsendtSoknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.Innsendt },
			innsendtSoknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.SendSenere })
		assertNotNull(kvitteringsDokument)

		// Skriver til tmp fil for manuell sjekk av innholdet av generert PDF
		//writeBytesToFile(kvitteringsDokument,"dummy.pdf")

	}

	@Test
	fun lagDummyHoveddokumentForEttersending() {
		val innsendingService = lagInnsendingService(soknadService)

		// Opprett original soknad
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

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

		val ettersending = OpprettEttersendingTestBuilder()
			.skjemanr(dokumentSoknadDto.skjemanr)
			.vedleggsListe(
				listOf(
					InnsendtVedleggDtoTestBuilder().vedleggsnr("W1").tittel("Vedlegg1").build(),
				)
			)
			.build()

		// Opprett ettersendingssoknad
		val ettersendingsSoknadDto =
			ettersendingService.createEttersendingFromExistingSoknader(dokumentSoknadDto.brukerId, ettersending)

		assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		assertEquals(1, ettersendingsSoknadDto.vedleggsListe.size)
		assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt })

		val dummyHovedDokument = PdfGenerator().lagForsideEttersending(ettersendingsSoknadDto)
		assertNotNull(dummyHovedDokument)

		// Skriver til tmp fil for manuell sjekk av innholdet av generert PDF
		//writeBytesToFile("dummy", ".pdf", dummyHovedDokument)
	}

	@Test
	fun innsendingFeilerNarIngenDokumentOpplastet() {
		val innsendingService = lagInnsendingService(soknadService)

		// Opprett original soknad
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		// Sender inn original soknad
		assertThrows<IllegalActionException> {
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		}
	}

	@Test
	fun sendInnSoknadMedVedlegg() {
		val innsendingService = lagInnsendingService(soknadService)
		val dokumentSoknadDto =
			SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "X1", "X2", "W2", "W3", "W4"))

		// Last opp fil til hoveddokument vedlegg
		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument })
		)

		// Last opp fil1 til W1 vedlegg
		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { !it.erHoveddokument && it.vedleggsnr == "W1" })
		)
		// Last opp fil2 til W1 vedlegg
		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { !it.erHoveddokument && it.vedleggsnr == "W1" })
		)

		vedleggService.endreVedlegg(
			patchVedleggDto =
			PatchVedleggDto(
				tittel = null,
				opplastingsStatus = OpplastingsStatusDto.SendSenere,
				opplastingsValgKommentarLedetekst = "Hvorfor sender du ikke nå",
				opplastingsValgKommentar = "Venter på dokumentasjonen fra min arbeidsgiver"
			),
			vedleggsId = dokumentSoknadDto.vedleggsListe.first { !it.erHoveddokument && it.vedleggsnr == "X1" }.id!!,
			soknadDto = dokumentSoknadDto,
			required = null
		)

		vedleggService.endreVedlegg(
			patchVedleggDto =
			PatchVedleggDto(
				tittel = null,
				opplastingsStatus = OpplastingsStatusDto.SendesAvAndre,
				opplastingsValgKommentarLedetekst = "Hvem sender inn vedlegget",
				opplastingsValgKommentar = "Min fastlege sender inn dokumentasjonen"
			),
			vedleggsId = dokumentSoknadDto.vedleggsListe.first { !it.erHoveddokument && it.vedleggsnr == "X2" }.id!!,
			soknadDto = dokumentSoknadDto,
			required = null
		)

		vedleggService.endreVedlegg(
			patchVedleggDto =
			PatchVedleggDto(
				tittel = null,
				opplastingsStatus = OpplastingsStatusDto.LevertDokumentasjonTidligere,
				opplastingsValgKommentarLedetekst = "I hvilken sammenheng sendte du inn denne dokumentasjonen til NAV",
				opplastingsValgKommentar = "I forbindelse med en tidligere innsendt søknad"
			),
			vedleggsId = dokumentSoknadDto.vedleggsListe.first { !it.erHoveddokument && it.vedleggsnr == "W2" }.id!!,
			soknadDto = dokumentSoknadDto,
			required = null
		)

		vedleggService.endreVedlegg(
			patchVedleggDto =
			PatchVedleggDto(
				tittel = null,
				opplastingsStatus = OpplastingsStatusDto.HarIkkeDokumentasjonen,
				opplastingsValgKommentarLedetekst = "Forklar hvorfor du ikke har denne informasjonen",
				opplastingsValgKommentar = "Jeg har aldri fått denne fra min sønns skole"
			),
			vedleggsId = dokumentSoknadDto.vedleggsListe.first { !it.erHoveddokument && it.vedleggsnr == "W3" }.id!!,
			soknadDto = dokumentSoknadDto,
			required = null
		)


		vedleggService.endreVedlegg(
			patchVedleggDto =
			PatchVedleggDto(
				tittel = null,
				opplastingsStatus = OpplastingsStatusDto.NavKanHenteDokumentasjon,
				opplastingsValgKommentarLedetekst = "Bekreft at det du gir NAV tillatelse til å hente denne dokumentasjonen",
				opplastingsValgKommentar = "Jeg bekrefter at NAV kan ta kontakt med min tidligere samboer for å få denne dokumentasjonen"
			),
			vedleggsId = dokumentSoknadDto.vedleggsListe.first { !it.erHoveddokument && it.vedleggsnr == "W4" }.id!!,
			soknadDto = dokumentSoknadDto,
			required = null
		)
		val oppdatertDokumentDto = soknadService.hentSoknad(dokumentSoknadDto.id!!)

		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				oppdatertDokumentDto,
				innsendingService
			)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)
		assertEquals(1, kvitteringsDto.innsendteVedlegg!!.size)
		assertEquals(1, kvitteringsDto.skalEttersendes!!.size)
		assertEquals(1, kvitteringsDto.skalSendesAvAndre!!.size)
		assertEquals(1, kvitteringsDto.sendesIkkeInn!!.size)
		assertEquals(1, kvitteringsDto.levertTidligere!!.size)
		assertEquals(1, kvitteringsDto.navKanInnhente!!.size)

		assertThrows<IllegalActionException> {
			vedleggService.leggTilVedlegg(dokumentSoknadDto, null)
		}

		// Hvis hent innsendt hoveddokument
		val vedleggsFiler = innsendingService.getFiles(
			oppdatertDokumentDto.innsendingsId!!,
			oppdatertDokumentDto.vedleggsListe.map { it.uuid!! }.toList()
		)

		// Så skal
		assertEquals(7, vedleggsFiler.size)
		assertEquals(2, vedleggsFiler.filter { it.fileStatus == SoknadFile.FileStatus.ok }.size)
		assertEquals(
			2,
			AntallSider().finnAntallSider(vedleggsFiler.filter { it.fileStatus == SoknadFile.FileStatus.ok }.last().content)
		)

	}


}

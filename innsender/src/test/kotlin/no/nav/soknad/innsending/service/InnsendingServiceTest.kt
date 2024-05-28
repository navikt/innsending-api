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
import no.nav.soknad.innsending.utils.Hjelpemetoder.Companion.writeBytesToFile
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
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1", "X1"))

		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument })
		)

		val oppdatertVedlegg = vedleggService.endreVedlegg(
			patchVedleggDto = PatchVedleggDto(tittel = null, opplastingsStatus = OpplastingsStatusDto.NavKanHenteDokumentasjon, opplastingsValgKommentar = "NAV kan innhente inntektsopplysninger for meg fra skatt"),
			vedleggsId = dokumentSoknadDto.vedleggsListe.first { it.vedleggsnr == "X1" }.id!!,
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
		writeBytesToFile(kvitteringsDokument,"dummy.pdf")

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
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		// Last opp fil til hoveddokument vedlegg
		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument })
		)

		// Last opp fil1 til W1 vedlegg
		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { !it.erHoveddokument })
		)
		// Last opp fil2 til W1 vedlegg
		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { !it.erHoveddokument })
		)

		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isNotEmpty())
		assertTrue(kvitteringsDto.skalEttersendes!!.isEmpty())

		assertThrows<IllegalActionException> {
			vedleggService.leggTilVedlegg(dokumentSoknadDto, null)
		}

		// Hvis hent innsendt hoveddokument
		val vedleggsFiler = innsendingService.getFiles(
			dokumentSoknadDto.innsendingsId!!,
			dokumentSoknadDto.vedleggsListe.map { it.uuid!! }.toList()
		)

		// Så skal
		assertEquals(2, vedleggsFiler.size)
		assertTrue(vedleggsFiler.all { it.fileStatus == SoknadFile.FileStatus.ok })
		assertEquals(2, AntallSider().finnAntallSider(vedleggsFiler.last().content))

	}



}

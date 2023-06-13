package no.nav.soknad.innsending.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.SoknadAssertions
import no.nav.soknad.pdfutilities.AntallSider
import no.nav.soknad.pdfutilities.PdfGenerator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

class InnsendingServiceTest : ApplicationTest() {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	private lateinit var soknadService: SoknadService

	@Autowired
	private lateinit var filService: FilService

	@Autowired
	private lateinit var restConfig: RestConfig

	@Autowired
	private lateinit var vedleggService: VedleggService

	@Autowired
	private lateinit var ettersendingService: EttersendingService

	@Autowired
	private lateinit var exceptionHelper: ExceptionHelper

	@InjectMockKs
	private val fillagerAPI = mockk<FillagerInterface>()

	@InjectMockKs
	private val soknadsmottakerAPI = mockk<MottakerInterface>()

	@InjectMockKs
	private val brukernotifikasjonPublisher = mockk<BrukernotifikasjonPublisher>()

	@InjectMockKs
	private val pdlInterface = mockk<PdlInterface>()

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

	@BeforeEach
	fun setup() {
		every { brukernotifikasjonPublisher.soknadStatusChange(any()) } returns true
		every { pdlInterface.hentPersonData(any()) } returns PersonDto("1234567890", "Kan", null, "Søke")
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
				fillagerAPI,
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		Assertions.assertTrue(kvitteringsDto.hoveddokumentRef != null)
		Assertions.assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		Assertions.assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty())

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
		Assertions.assertEquals(1, hoveddok.size)
		Assertions.assertTrue(hoveddok.all { it.status == "ok" })

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
				fillagerAPI,
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		Assertions.assertTrue(kvitteringsDto.hoveddokumentRef != null)
		Assertions.assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		Assertions.assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty())

		val ettersendingsSoknadDto =
			soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		Assertions.assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		Assertions.assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })

		assertThrows<IllegalActionException> {
			innsendingService.sendInnSoknad(ettersendingsSoknadDto)
		}
	}

	@Test
	fun lagKvitteringsHoveddokument() {
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
				fillagerAPI,
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		Assertions.assertTrue(kvitteringsDto.hoveddokumentRef != null)
		Assertions.assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		Assertions.assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty())


		// Test generering av kvittering for innsendt soknad.
		// Merk det er besluttet og ikke sende kvittering med innsendingen av søknaden. Det innebærer at denne koden pt er redundant
		val innsendtSoknad = soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)
		val kvitteringsDokument = PdfGenerator().lagKvitteringsSide(innsendtSoknad, "Per Person",
			innsendtSoknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt },
			innsendtSoknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.sendSenere })
		Assertions.assertTrue(kvitteringsDokument != null)

		// Skriver til tmp fil for manuell sjekk av innholdet av generert PDF
//		writeBytesToFile("dummy", ".pdf", kvitteringsDokument)

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
				fillagerAPI,
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		Assertions.assertTrue(kvitteringsDto.hoveddokumentRef != null)
		Assertions.assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		Assertions.assertTrue(kvitteringsDto.skalEttersendes!!.isNotEmpty())

		// Opprett ettersendingssoknad
		val ettersendingsSoknadDto =
			soknadService.opprettSoknadForettersendingAvVedlegg(dokumentSoknadDto.brukerId, dokumentSoknadDto.innsendingsId!!)

		Assertions.assertTrue(ettersendingsSoknadDto.vedleggsListe.isNotEmpty())
		Assertions.assertTrue(ettersendingsSoknadDto.vedleggsListe.none { it.opplastingsStatus == OpplastingsStatusDto.innsendt })
		Assertions.assertTrue(ettersendingsSoknadDto.vedleggsListe.any { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt })

		val dummyHovedDokument = PdfGenerator().lagForsideEttersending(ettersendingsSoknadDto)
		Assertions.assertTrue(dummyHovedDokument != null)

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
				fillagerAPI,
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
				fillagerAPI,
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		Assertions.assertTrue(kvitteringsDto.hoveddokumentRef != null)
		Assertions.assertTrue(kvitteringsDto.innsendteVedlegg!!.isNotEmpty())
		Assertions.assertTrue(kvitteringsDto.skalEttersendes!!.isEmpty())

		assertThrows<IllegalActionException> {
			vedleggService.leggTilVedlegg(dokumentSoknadDto, null)
		}
		//soknadService.hentSoknad(dokumentSoknadDto.innsendingsId!!)

		// Hvis hent innsendt hoveddokument
		val vedleggsFiler = innsendingService.getFiles(
			dokumentSoknadDto.innsendingsId!!,
			dokumentSoknadDto.vedleggsListe.map { it.uuid!! }.toList()
		)

		// Så skal
		Assertions.assertEquals(2, vedleggsFiler.size)
		Assertions.assertTrue(vedleggsFiler.all { it.status == "ok" })
		Assertions.assertEquals(2, AntallSider().finnAntallSider(vedleggsFiler.last().content))

	}


}

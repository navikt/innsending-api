package no.nav.soknad.innsending.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.model.PatchVedleggDto
import no.nav.soknad.innsending.model.PostVedleggDto
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.SoknadAssertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

class VedleggServiceTest : ApplicationTest() {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	private lateinit var skjemaService: SkjemaService

	@Autowired
	private lateinit var vedleggService: VedleggService

	@Autowired
	private lateinit var ettersendingService: EttersendingService

	@Autowired
	private lateinit var filService: FilService

	@Autowired
	private lateinit var exceptionHelper: ExceptionHelper

	@InjectMockKs
	private val brukernotifikasjonPublisher = mockk<BrukernotifikasjonPublisher>()

	@InjectMockKs
	private val subjectHandler = mockk<SubjectHandlerInterface>()

	private fun lagSoknadService(): SoknadService = SoknadService(
		skjemaService = skjemaService,
		repo = repo,
		vedleggService = vedleggService,
		filService = filService,
		brukernotifikasjonPublisher = brukernotifikasjonPublisher,
		innsenderMetrics = innsenderMetrics,
		exceptionHelper = exceptionHelper,
		subjectHandler = subjectHandler,
	)

	@BeforeEach
	fun setup() {
		every { brukernotifikasjonPublisher.soknadStatusChange(any()) } returns true
	}

	@Test
	fun hentOpprettetVedlegg() {
		val soknadService = lagSoknadService()

		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = vedleggService.hentVedleggDto(dokumentSoknadDto.vedleggsListe[0].id!!)

		assertEquals(vedleggDto.id, dokumentSoknadDto.vedleggsListe[0].id!!)

		val filDtoListe =
			filService.hentFiler(dokumentSoknadDto, dokumentSoknadDto.innsendingsId!!, vedleggDto.id!!, false)
		assertTrue(filDtoListe.isEmpty())
	}

	@Test
	fun leggTilVedlegg() {
		val soknadService = lagSoknadService()

		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val postVedleggDto = PostVedleggDto("Litt mer info")

		val lagretVedleggDto = vedleggService.leggTilVedlegg(dokumentSoknadDto, postVedleggDto)

		assertNotNull(lagretVedleggDto.id)
		assertEquals("N6", lagretVedleggDto.vedleggsnr)
		assertEquals("Litt mer info", lagretVedleggDto.tittel)
	}

	@Test
	fun oppdaterVedleggEndrerKunTittelOgLabel() {
		// Når søker har endret label på et vedlegg av type annet (N6), skal tittel settes lik label og vedlegget i databasen oppdateres med disse endringene.
		val soknadService = lagSoknadService()

		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val lagretVedleggDto = vedleggService.leggTilVedlegg(dokumentSoknadDto, null)
		assertTrue(lagretVedleggDto.id != null && lagretVedleggDto.tittel == "Annet")

		val patchVedleggDto = PatchVedleggDto("Ny tittel", lagretVedleggDto.opplastingsStatus)
		val oppdatertVedleggDto = vedleggService.endreVedlegg(patchVedleggDto, lagretVedleggDto.id!!, dokumentSoknadDto)

		assertTrue(
			oppdatertVedleggDto.id == lagretVedleggDto.id && oppdatertVedleggDto.tittel == "Ny tittel" && oppdatertVedleggDto.vedleggsnr == "N6"
				&& oppdatertVedleggDto.label == oppdatertVedleggDto.tittel
		)
	}

	@Test
	fun slettOpprettetVedlegg() {
		val soknadService = lagSoknadService()

		val vedleggsnr = "N6"
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf(vedleggsnr))

		val lagretVedlegg = dokumentSoknadDto.vedleggsListe.first { e -> vedleggsnr == e.vedleggsnr }

		vedleggService.slettVedlegg(dokumentSoknadDto, lagretVedlegg.id!!)

		assertThrows<Exception> {
			vedleggService.hentVedleggDto(lagretVedlegg.id!!)
		}
	}
}

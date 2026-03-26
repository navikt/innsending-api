package no.nav.soknad.innsending.cleanup

import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.pdl.PdlAPI
import no.nav.soknad.innsending.consumerapis.pdl.dto.IdentDto
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.FilDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.FilService
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertTrue

class TempCleanupArchiveFailureTest : ApplicationTest() {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var filService: FilService

	@Autowired
	private lateinit var soknadService: SoknadService

	@Autowired
	private lateinit var innsendingService: InnsendingService

	@Autowired
	private lateinit var repositoryUtils: RepositoryUtils

	@MockkBean
	private lateinit var leaderSelectionUtility: LeaderSelectionUtility

	@MockkBean
	private lateinit var soknadsmottakerAPI: MottakerAPITest

	@MockkBean
	private lateinit var pdlInterface: PdlAPI

	@MockkBean
	private lateinit var subjectHandler: SubjectHandlerInterface

	@BeforeEach
	fun setup() {
		every { leaderSelectionUtility.isLeader() } returns true
		every { soknadsmottakerAPI.sendInnSoknad(any(), any(), any(), any()) } returns Unit
		every { pdlInterface.hentPersonIdents(any()) } returns listOf(IdentDto("123456789", "FOLKEREGISTERIDENT", false))
		every { pdlInterface.hentPersonData(any()) } returns PersonDto("123456789", "Fornavn", null, "Etternavn")
		every { subjectHandler.getClientId() } returns "application"
	}

	@Test
	fun `oppdaterer vedlegg uten filer og sender inn igjen ved ArkiveringFeilet`() {
		val innsendingsId = opprettInnsendtSoknad()
		simulerArkiveringsRespons(innsendingsId, ArkiveringsStatus.ArkiveringFeilet)

		val soknadDb = repo.hentSoknadDb(innsendingsId)
		val eksisterendeVedleggUtenFil = repo.hentAlleVedleggGittSoknadsid(soknadDb.id!!)
			.first { repo.countFiles(innsendingsId, it.id!!) == 0 }
		val eksisterendeVedleggMedFil = repo.hentAlleVedleggGittSoknadsid(soknadDb.id!!)
			.first { repo.countFiles(innsendingsId, it.id!!) > 0 }

		clearMocks(soknadsmottakerAPI)
		every { soknadsmottakerAPI.sendInnSoknad(any(), any(), any(), any()) } returns Unit

		val job = TempCleanupArchiveFailure(
			leaderSelectionUtility = leaderSelectionUtility,
			repo = repo,
			soknadService = soknadService,
			mottakerApi = soknadsmottakerAPI,
			innsendingsids = innsendingsId,
		)
		job.fixAttachmentStatusAndResubmit()

		val slotVedleggsliste = slot<List<VedleggDto>>()
		verify(exactly = 1) {
			soknadsmottakerAPI.sendInnSoknad(any(), capture(slotVedleggsliste), any(), any())
		}
		assertEquals(1, slotVedleggsliste.captured.size)
		assertTrue(slotVedleggsliste.captured.none { it.vedleggsnr == eksisterendeVedleggUtenFil.vedleggsnr })
		assertTrue(slotVedleggsliste.captured.all { it.vedleggsnr == eksisterendeVedleggMedFil.vedleggsnr })
	}

	@Test
	fun `sender ikke inn igjen nar arkiveringsstatus ikke er ArkiveringFeilet`() {
		val innsendingsId = opprettInnsendtSoknad()
		simulerArkiveringsRespons(innsendingsId, ArkiveringsStatus.Arkivert)

		clearMocks(soknadsmottakerAPI)
		every { soknadsmottakerAPI.sendInnSoknad(any(), any(), any(), any()) } returns Unit

		val job = TempCleanupArchiveFailure(
			leaderSelectionUtility = leaderSelectionUtility,
			repo = repo,
			soknadService = soknadService,
			mottakerApi = soknadsmottakerAPI,
			innsendingsids = innsendingsId,
		)
		job.fixAttachmentStatusAndResubmit()

		verify(exactly = 0) { soknadsmottakerAPI.sendInnSoknad(any(), any(), any(), any()) }
	}

	private fun opprettInnsendtSoknad(): String {
		val soknad = repositoryUtils.lagreSoknad(
			SoknadDbDataTestBuilder(
				skjemanr = "NAV 55-00.60",
				brukerId = "12345678901",
				spraak = "nb",
				innsendingsId = UUID.randomUUID().toString(),
				status = SoknadsStatus.Innsendt,
			).build()
		)
		val vedleggsnrMedFil = "V1"
		val vedleggsnrUtenFil = "V2"
		val allAttachments = listOf(
			VedleggDbData(
				id = null,
				soknadsid = soknad.id!!,
				status = OpplastingsStatus.KLAR_FOR_INNSENDING,
				erhoveddokument = false,
				ervariant = false,
				erpdfa = true,
				erpakrevd = false,
				vedleggsnr = vedleggsnrMedFil,
				tittel = "Testvedlegg1",
				label = "Testlabel1",
				beskrivelse = "Testbeskrivelse1",
				mimetype = "application/pdf",
				uuid = UUID.randomUUID().toString(),
				opprettetdato = LocalDateTime.now(),
				endretdato = LocalDateTime.now(),
				innsendtdato = null,
				vedleggsurl = null,
				formioid = null,
				opplastingsvalgkommentarledetekst = null,
				opplastingsvalgkommentar = null,
				fileIds = null,
			),
			VedleggDbData(
				id = null,
				soknadsid = soknad.id!!,
				status = OpplastingsStatus.KLAR_FOR_INNSENDING,
				erhoveddokument = false,
				ervariant = false,
				erpdfa = true,
				erpakrevd = false,
				vedleggsnr = vedleggsnrUtenFil,
				tittel = "Testvedlegg2",
				label = "Testlabel2",
				beskrivelse = "Testbeskrivelse2",
				mimetype = "application/pdf",
				uuid = UUID.randomUUID().toString(),
				opprettetdato = LocalDateTime.now(),
				endretdato = LocalDateTime.now(),
				innsendtdato = null,
				vedleggsurl = null,
				formioid = null,
				opplastingsvalgkommentarledetekst = null,
				opplastingsvalgkommentar = null,
				fileIds = null,
			)
		).map {
			repositoryUtils.lagreVedlegg(it)
		}
		val data = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
		repositoryUtils.saveFilDbData(
			soknad.innsendingsid,
			FilDbData(
				id = null,
				vedleggsid = allAttachments.first { it.vedleggsnr == vedleggsnrMedFil }.id!!,
				filnavn = "test",
				mimetype = "application/pdf",
				storrelse = data.size,
				data = data,
				opprettetdato = LocalDateTime.now(),
				antallsider = 1,
			)
		)
		return soknad.innsendingsid
	}

	private fun simulerArkiveringsRespons(innsendingsId: String, arkiveringsStatus: ArkiveringsStatus) {
		val soknad = repo.hentSoknadDb(innsendingsId)
		repo.oppdaterArkiveringsstatus(soknad, arkiveringsStatus)
	}
}

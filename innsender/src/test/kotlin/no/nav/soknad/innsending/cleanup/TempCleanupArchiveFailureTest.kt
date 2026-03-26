package no.nav.soknad.innsending.cleanup

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.SoknadService
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.test.assertEquals

class TempCleanupArchiveFailureTest {

	@Test
	fun `oppdaterer vedlegg uten filer og sender inn igjen ved ArkiveringFeilet`() {
		val leaderSelection = mockk<LeaderSelection>()
		val repo = mockk<RepositoryUtils>()
		val soknadService = mockk<SoknadService>()
		val mottakerApi = mockk<MottakerInterface>()

		val innsendingsId = "id-1"
		val now = LocalDateTime.now()
		val soknadDb = SoknadDbData(
			id = 1L,
			innsendingsid = innsendingsId,
			tittel = "tittel",
			skjemanr = "NAV 55-00.60",
			tema = "BID",
			spraak = "no",
			status = no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus.Innsendt,
			brukerid = "12345678901",
			ettersendingsid = null,
			opprettetdato = now,
			endretdato = now,
			innsendtdato = now,
			visningssteg = 0,
			visningstype = VisningsType.fyllUt,
			kanlasteoppannet = true,
			forsteinnsendingsdato = null,
			ettersendingsfrist = 14,
			arkiveringsstatus = ArkiveringsStatus.ArkiveringFeilet,
			applikasjon = "application",
			skalslettesdato = OffsetDateTime.now().plusDays(10),
			ernavopprettet = false,
			brukertype = BrukerDto.IdType.FNR,
			avsender = AvsenderDto(id = "12345678901", idType = AvsenderDto.IdType.FNR),
			affecteduser = BrukerDto(id = "12345678901", idType = BrukerDto.IdType.FNR)
		)
		val vedleggMedFil = lagVedlegg(id = 10L, soknadsid = 1L, status = OpplastingsStatus.KLAR_FOR_INNSENDING, vedleggsnr = "W6")
		val vedleggUtenFil = lagVedlegg(id = 11L, soknadsid = 1L, status = OpplastingsStatus.KLAR_FOR_INNSENDING, vedleggsnr = "W7")

		val soknadDto = DokumentSoknadDto(
			innsendingsId = innsendingsId,
			skjemanr = "NAV 55-00.60",
			tittel = "tittel",
			tema = "BID",
			status = SoknadsStatusDto.Innsendt,
			opprettetDato = OffsetDateTime.now(),
			vedleggsListe = listOf(
				vedleggDto(id = 1L, erHoveddokument = true, erVariant = false, status = OpplastingsStatusDto.KlarForInnsending, vedleggsnr = "NAV 55-00.60"),
				vedleggDto(id = 2L, erHoveddokument = true, erVariant = true, status = OpplastingsStatusDto.KlarForInnsending, vedleggsnr = "NAV 55-00.60"),
				vedleggDto(id = 3L, erHoveddokument = false, erVariant = false, status = OpplastingsStatusDto.KlarForInnsending, vedleggsnr = "W6"),
			),
			id = 1L,
			brukerId = "12345678901",
			spraak = "no",
			visningsSteg = 0L,
			visningsType = VisningsType.fyllUt,
			kanLasteOppAnnet = true,
			arkiveringsStatus = ArkiveringsStatusDto.ArkiveringFeilet,
			soknadstype = SoknadType.soknad,
			skjemaPath = "nav550060",
			applikasjon = "application",
			skalSlettesDato = OffsetDateTime.now().plusDays(10),
		)

		every { leaderSelection.isLeader() } returns true
		every { repo.existsByInnsendingsId(innsendingsId) } returns true
		every { repo.hentSoknadDb(innsendingsId) } returns soknadDb
		every { repo.hentAlleVedleggGittSoknadsid(1L) } returns listOf(vedleggMedFil, vedleggUtenFil)
		every { repo.countFiles(innsendingsId, 10L) } returns 1
		every { repo.countFiles(innsendingsId, 11L) } returns 0
		every { repo.updateVedleggStatus(innsendingsId, 11L, OpplastingsStatus.INNSENDT) } returns 1
		every { soknadService.hentSoknad(innsendingsId) } returns soknadDto
		every { mottakerApi.sendInnSoknad(any(), any(), any(), any()) } returns Unit

		val job = TempCleanupArchiveFailure(
			leaderSelectionUtility = leaderSelection,
			repo = repo,
			soknadService = soknadService,
			mottakerApi = mottakerApi,
			innsendingsids = innsendingsId
		)

		job.fixAttachmentStatusAndResubmit()

		verify(exactly = 1) { repo.updateVedleggStatus(innsendingsId, 11L, OpplastingsStatus.INNSENDT) }
		verify(exactly = 0) { repo.updateVedleggStatus(innsendingsId, 10L, any()) }

		val vedleggCaptor = slot<List<VedleggDto>>()
		verify(exactly = 1) { mottakerApi.sendInnSoknad(soknadDto, capture(vedleggCaptor), any(), any()) }
		assertEquals(3, vedleggCaptor.captured.size)
	}

	@Test
	fun `sender ikke inn igjen nar arkiveringsstatus ikke er ArkiveringFeilet`() {
		val leaderSelection = mockk<LeaderSelection>()
		val repo = mockk<RepositoryUtils>()
		val soknadService = mockk<SoknadService>()
		val mottakerApi = mockk<MottakerInterface>()
		val innsendingsId = "id-2"
		val now = LocalDateTime.now()

		val soknadDb = SoknadDbData(
			id = 2L,
			innsendingsid = innsendingsId,
			tittel = "tittel",
			skjemanr = "NAV 55-00.60",
			tema = "BID",
			spraak = "no",
			status = no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus.Innsendt,
			brukerid = "12345678901",
			ettersendingsid = null,
			opprettetdato = now,
			endretdato = now,
			innsendtdato = now,
			visningssteg = 0,
			visningstype = VisningsType.fyllUt,
			kanlasteoppannet = true,
			forsteinnsendingsdato = null,
			ettersendingsfrist = 14,
			arkiveringsstatus = ArkiveringsStatus.Arkivert,
			applikasjon = "application",
			skalslettesdato = OffsetDateTime.now().plusDays(10),
			ernavopprettet = false,
			brukertype = BrukerDto.IdType.FNR,
			avsender = null,
			affecteduser = null
		)

		every { leaderSelection.isLeader() } returns true
		every { repo.existsByInnsendingsId(innsendingsId) } returns true
		every { repo.hentSoknadDb(innsendingsId) } returns soknadDb

		val job = TempCleanupArchiveFailure(
			leaderSelectionUtility = leaderSelection,
			repo = repo,
			soknadService = soknadService,
			mottakerApi = mottakerApi,
			innsendingsids = innsendingsId
		)

		job.fixAttachmentStatusAndResubmit()

		verify(exactly = 0) { repo.hentAlleVedleggGittSoknadsid(any()) }
		verify(exactly = 0) { soknadService.hentSoknad(any<String>()) }
		verify(exactly = 0) { mottakerApi.sendInnSoknad(any(), any(), any(), any()) }
	}

	private fun lagVedlegg(id: Long, soknadsid: Long, status: OpplastingsStatus, vedleggsnr: String): VedleggDbData {
		val now = LocalDateTime.now()
		return VedleggDbData(
			id = id,
			soknadsid = soknadsid,
			status = status,
			erhoveddokument = false,
			ervariant = false,
			erpdfa = false,
			erpakrevd = false,
			vedleggsnr = vedleggsnr,
			tittel = "vedlegg",
			label = "vedlegg",
			beskrivelse = "",
			mimetype = "application/pdf",
			uuid = "uuid-$id",
			opprettetdato = now,
			endretdato = now,
			innsendtdato = null,
			vedleggsurl = null,
			formioid = null,
			opplastingsvalgkommentarledetekst = null,
			opplastingsvalgkommentar = null,
			fileIds = null,
		)
	}

	private fun vedleggDto(
		id: Long,
		erHoveddokument: Boolean,
		erVariant: Boolean,
		status: OpplastingsStatusDto,
		vedleggsnr: String,
	): VedleggDto {
		return VedleggDto(
			tittel = "vedlegg",
			label = "vedlegg",
			erHoveddokument = erHoveddokument,
			erVariant = erVariant,
			erPdfa = false,
			erPakrevd = true,
			opplastingsStatus = status,
			opprettetdato = OffsetDateTime.now(),
			id = id,
			vedleggsnr = vedleggsnr,
			beskrivelse = "",
			uuid = "uuid-$id",
			mimetype = Mimetype.applicationSlashPdf,
			document = null,
			skjemaurl = null,
			innsendtdato = OffsetDateTime.now(),
			formioId = null,
			opplastingsValgKommentarLedetekst = null,
			opplastingsValgKommentar = null,
			fileIds = null,
		)
	}
}

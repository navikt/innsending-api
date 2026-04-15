package no.nav.soknad.innsending.cleanup

import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.SoknadService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class TempCleanupArchiveFailure(
	private val repo: RepositoryUtils,
	private val soknadService: SoknadService,
	private val mottakerApi: MottakerInterface,
	@param:Value("\${tempFailedApplications}")
	private var innsendingsids: String,
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun fixAttachmentStatusAndResubmit() {
		logger.info("Invoked fixAttachmentStatusAndResubmit for configured failed applications")
		val idsToProcess = innsendingsids
			.split(",")
			.map { it.trim() }
			.filter { it.isNotEmpty() }
		logger.info("Starting fixAttachmentStatusAndResubmit for ${idsToProcess.size} innsendingId(s)")
		idsToProcess.forEach { innsendingsId ->
			try {
				if (!repo.existsByInnsendingsId(innsendingsId)) {
					logger.warn("$innsendingsId: søknad finnes ikke i databasen, hopper over")
					return@forEach
				}

				val soknadDb = repo.hentSoknadDb(innsendingsId)
				if (soknadDb.arkiveringsstatus != ArkiveringsStatus.ArkiveringFeilet) {
					logger.info("$innsendingsId: arkiveringsstatus=${soknadDb.arkiveringsstatus}, hopper over")
					return@forEach
				}

				val vedleggTilOppdatering = repo.hentAlleVedleggGittSoknadsid(soknadDb.id!!)
					.filter { vedlegg ->
						vedlegg.status == OpplastingsStatus.KLAR_FOR_INNSENDING &&
							repo.countFiles(innsendingsId, vedlegg.id!!) == 0
					}

				vedleggTilOppdatering.forEach { vedlegg ->
					repo.updateVedleggStatus(
						innsendingsId = innsendingsId,
						vedleggsId = vedlegg.id!!,
						opplastingsStatus = OpplastingsStatus.INNSENDT
					)
					logger.info("$innsendingsId: vedlegg ${vedlegg.id} endret fra KLAR_FOR_INNSENDING til INNSENDT (ingen filer i tabell fil)")
				}

				val soknadDto = soknadService.hentSoknad(innsendingsId)
				val vedleggTilInnsending = soknadDto.vedleggsListe.filter {
					(it.erHoveddokument && it.opplastingsStatus != OpplastingsStatusDto.SendesIkke) ||
						it.opplastingsStatus == OpplastingsStatusDto.KlarForInnsending
				}
				val avsender = soknadDb.avsender ?: AvsenderDto(
					id = soknadDb.brukerid,
					idType = AvsenderDto.IdType.FNR,
				)
				val bruker = soknadDb.affecteduser ?: soknadDb.brukerid?.let {
					BrukerDto(id = it, idType = BrukerDto.IdType.FNR)
				}

				mottakerApi.sendInnSoknad(soknadDto, vedleggTilInnsending, avsender, bruker)
				logger.info("$innsendingsId: sendt inn på nytt med ${vedleggTilInnsending.size} dokument(er)")
			} catch (ex: Exception) {
				logger.error("$innsendingsId: feilet ved oppdatering og resending av søknad", ex)
			}
		}
		logger.info("Finished fixAttachmentStatusAndResubmit")
	}

}

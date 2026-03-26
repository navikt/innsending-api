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
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@EnableScheduling
@Component
class TempCleanupArchiveFailure(
	private val leaderSelectionUtility: LeaderSelection,
	private val repo: RepositoryUtils,
	private val soknadService: SoknadService,
	private val mottakerApi: MottakerInterface,
	@param:Value($$"${tempFailedApplications}")
	private var innsendingsids: String,
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Scheduled(cron = cron)
	fun fixAttachmentStatusAndResubmit() {
		if (!leaderSelectionUtility.isLeader()) {
			return
		}

		innsendingsids
			.split(",")
			.map { it.trim() }
			.forEach { innsendingsId ->
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
	}

}

// 14:30:00 on 26 March every year
private const val cron = "0 30 14 26 3 *"

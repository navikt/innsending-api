package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.repository.*
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Service
class RepositoryUtils(
	private val soknadRepository: SoknadRepository,
	private val vedleggRepository: VedleggRepository,
	private val filRepository: FilRepository,
	private val filWithoutDataRepository: FilWithoutDataRepository,
	private val hendelseRepository: HendelseRepository
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun hentSoknadDb(id: Long): SoknadDbData = try {
		soknadRepository.findByIdOrNull(id) ?: throw ResourceNotFoundException(
			"Fant ikke søknad med soknadsId $id",
			"errorCode.resourceNotFound.applicationNotFound"
		)
	} catch (e: ResourceNotFoundException) {
		throw e
	} catch (re: Exception) {
		throw BackendErrorException(
			re.message,
			"Henting av søknad $id fra databasen feilet",
			"errorCode.backendError.applicationFetchError"
		)
	}

	fun hentSoknadDb(innsendingsId: String): SoknadDbData = try {
		soknadRepository.findByInnsendingsid(innsendingsId) ?: throw ResourceNotFoundException(
			message = "Fant ikke søknad med innsendingsid $innsendingsId",
			errorCode = "errorCode.resourceNotFound.applicationNotFound"
		)
	} catch (e: ResourceNotFoundException) {
		throw e
	} catch (re: Exception) {
		throw BackendErrorException(
			re.message,
			"Henting av søknad $innsendingsId fra databasen feilet",
			"errorCode.backendError.applicationFetchError"
		)
	}

	fun endreSoknadDb(id: Long, visningsSteg: Long) = try {
		soknadRepository.updateVisningsStegAndEndretDato(id, visningsSteg, LocalDateTime.now())
	} catch (re: Exception) {
		throw BackendErrorException(
			re.message,
			"Oppdatering av søknad med id= $id feilet",
			"errorCode.backendError.errorCode.backendError.applicationUpdateErro"
		)
	}

	fun findAllByStatusesAndWithOpprettetdatoBefore(statuses: List<String>, opprettetFor: OffsetDateTime) = try {
		soknadRepository.findAllByStatusesAndWithOpprettetdatoBefore(statuses, opprettetFor)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved henting av alle soknader med status $statuses opprettet før $opprettetFor",
			"errorCode.backendError.applicationFetchError"
		)
	}

	fun findAllByOpprettetdatoBefore(opprettetFor: OffsetDateTime) = try {
		soknadRepository.findAllByOpprettetdatoBefore(opprettetFor)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved henting av alle soknader opprettet før $opprettetFor",
			"errorCode.backendError.applicationFetchError"
		)
	}

	fun finnAlleSoknaderGittBrukerIdOgStatus(brukerId: String, status: SoknadsStatus) = try {
		soknadRepository.findByBrukeridAndStatus(brukerId, status)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved henting av alle soknader for bruker xxxx med status $status",
			"errorCode.backendError.applicationFetchError"
		)
	}

	fun finnNyesteSoknadGittEttersendingsId(ettersendingsId: String): List<SoknadDbData> = try {
		soknadRepository.findNewestByEttersendingsId(ettersendingsId)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved henting av nyeste søknad gitt ettersendingsid $$ettersendingsId",
			"errorCode.backendError.applicationFetchError"
		)
	}

	fun findAllSoknadBySoknadsstatusAndArkiveringsstatusAndBetweenInnsendtdatos(
		eldreEnn: Long,
		vindu: Long
	): List<SoknadDbData> =
		try {
			soknadRepository.finnAlleSoknaderBySoknadsstatusAndArkiveringsstatusAndBetweenInnsendtdatos(
				LocalDateTime.now().minusDays(eldreEnn + vindu), LocalDateTime.now().minusDays(eldreEnn)
			)
		} catch (ex: Exception) {
			throw BackendErrorException(
				ex.message,
				"Feil ved henting av alle arkiverte søknader arkivert mellom ${(vindu + eldreEnn)} og $eldreEnn dager siden",
				"errorCode.backendError.applicationFetchError"
			)
		}

	fun lagreSoknad(soknadDbData: SoknadDbData): SoknadDbData = try {
		lagreHendelse(soknadDbData)
		soknadRepository.save(soknadDbData)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil i lagring av søknad ${soknadDbData.tittel}",
			"errorCode.backendError.applicationSaveError"
		)
	}

	fun soknadSaveAndFlush(soknadDbData: SoknadDbData): SoknadDbData = try {
		soknadRepository.saveAndFlush(soknadDbData)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved lagring og flush av søknad ${soknadDbData.innsendingsid}",
			"errorCode.backendError.applicationSaveError"
		)
	}

	fun slettSoknad(dokumentSoknadDto: DokumentSoknadDto, hendelseType: HendelseType) = try {
		soknadRepository.deleteById(dokumentSoknadDto.id!!)
		lagreHendelse(
			HendelseDbData(
				id = null,
				innsendingsid = dokumentSoknadDto.innsendingsId!!,
				hendelsetype = hendelseType,
				tidspunkt = LocalDateTime.now(),
				skjemanr = dokumentSoknadDto.skjemanr,
				tema = dokumentSoknadDto.tema,
				erettersending = dokumentSoknadDto.ettersendingsId != null
			)
		)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved sletting av søknad ${dokumentSoknadDto.innsendingsId}",
			"errorCode.backendError.applicationDeleteError"
		)
	}

	fun oppdaterEndretDato(soknadsId: Long) = try {
		soknadRepository.updateEndretDato(soknadsId, LocalDateTime.now())
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved oppdatering av søknad med id $soknadsId",
			"errorCode.backendError.applicationUpdateError"
		)
	}

	fun oppdaterArkiveringsstatus(soknadDbData: SoknadDbData, arkiveringsStatus: ArkiveringsStatus) = try {
		soknadRepository.updateArkiveringsStatus(arkiveringsStatus, listOf(soknadDbData.innsendingsid))
		hendelseRepository.save(
			HendelseDbData(
				id = null,
				innsendingsid = soknadDbData.innsendingsid,
				hendelsetype = if (arkiveringsStatus == ArkiveringsStatus.Arkivert) HendelseType.Arkivert else HendelseType.ArkiveringFeilet,
				tidspunkt = LocalDateTime.now(),
				soknadDbData.skjemanr,
				soknadDbData.tema,
				erettersending = soknadDbData.ettersendingsid != null
			)
		)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved oppdatering av arkiveringsstatus på søknad med innsendingsId ${soknadDbData.innsendingsid}",
			"errorCode.backendError.applicationUpdateError"
		)
	}

	fun findNumberOfEventsByType(hendelseType: HendelseType): Long? = try {
		hendelseRepository.countByHendelsetype(hendelseType)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved henting av antall hendelser gitt hendelsetype $hendelseType",
			"errorCode.backendError.applicationFetchError"
		)
	}


	fun hentVedlegg(vedleggsId: Long): VedleggDbData = try {
		vedleggRepository.findByVedleggsid(vedleggsId) ?: throw ResourceNotFoundException(
			"Fant ikke vedlegg med id $vedleggsId",
			"errorCode.backendError.attachmentNotFound"
		)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved forsøk på henting av vedlegg med id $vedleggsId",
			"errorCode.backendError.attachmentFetchError"
		)
	}

	fun hentVedleggGittUuid(uuid: String): VedleggDbData? = try {
		vedleggRepository.findByUuid(uuid) ?: throw ResourceNotFoundException(
			"Fant ikke vedlegg med uuid $uuid",
			"errorCode.backendError.attachmentNotFound"
		)
	} catch (ex: ResourceNotFoundException) {
		throw ex
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved forsøk på henting av vedlegg med uuid $uuid",
			"errorCode.backendError.attachmentFetchError"
		)
	}

	fun hentAlleVedleggGittSoknadsid(soknadsId: Long): List<VedleggDbData> = try {
		vedleggRepository.findAllBySoknadsid(soknadsId)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved forsøk på henting av alle vedlegg til søknad med id $soknadsId",
			"errorCode.backendError.attachmentFetchError"
		)

	}

	fun lagreVedlegg(vedleggDbData: VedleggDbData): VedleggDbData = try {
		vedleggRepository.save(vedleggDbData)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil i lagring av vedleggsdata ${vedleggDbData.vedleggsnr} til søknad",
			"errorCode.backendError.attachmentSaveError"
		)
	}

	fun flushVedlegg() = try {
		vedleggRepository.flush()
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved flush av vedlegg", "errorCode.backendError.attachmentSaveError")
	}

	fun oppdaterVedlegg(innsendingsId: String, vedleggDbData: VedleggDbData): VedleggDbData = try {
		vedleggRepository.save(vedleggDbData)
		vedleggRepository.findByVedleggsid(vedleggDbData.id!!) ?: throw ResourceNotFoundException(
			"Fant ikke vedlegg med id ${vedleggDbData.id}",
			"errorCode.backendError.attachmentNotFound"
		)
	} catch (ex: ResourceNotFoundException) {
		throw ex
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved oppdatering av vedlegg ${vedleggDbData.id} for søknad $innsendingsId",
			"errorCode.backendError.attachmentSaveError"
		)
	}


	fun oppdaterVedleggStatus(
		innsendingsId: String,
		vedleggsId: Long,
		opplastingsStatus: OpplastingsStatus,
		localDateTime: LocalDateTime
	): Int = try {
		vedleggRepository.updateStatus(id = vedleggsId, status = opplastingsStatus, endretdato = localDateTime)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved oppdatering av status for vedlegg $vedleggsId for søknad $innsendingsId",
			"errorCode.backendError.attachmentSaveError"
		)
	}

	//NB! metoden vedleggRepository.updateStatusAndInnsendtdato fungerer ved lokal testing, men feiler når kjøring på nais.
	fun oppdaterVedleggStatusOgInnsendtdato(
		innsendingsId: String, vedleggsId: Long, opplastingsStatus: OpplastingsStatus,
		endretDato: LocalDateTime, innsendtDato: LocalDateTime
	): Int = try {
		logger.info("oppdaterVedleggStatusOgInnsendtdato: vedlegg=$vedleggsId, innsendtdato=$innsendtDato ")
		val raderEndret = vedleggRepository.updateStatusAndInnsendtdato(
			id = vedleggsId, status = opplastingsStatus, endretdato = endretDato, innsendtdato = innsendtDato
		)
		if (raderEndret != 1) {
			logger.error("$innsendingsId: oppdaterVedleggStatusOgInnsendtdato: uventet antall, $raderEndret, rader endret for vedlegg $vedleggsId")
		}
		raderEndret
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved oppdatering av status for vedlegg $vedleggsId for søknad $innsendingsId",
			"errorCode.backendError.attachmentSaveError"
		)
	}

	fun slettVedlegg(vedleggsId: Long) =
		try {
			vedleggRepository.deleteById(vedleggsId)
		} catch (ex: Exception) {
			throw BackendErrorException(
				ex.message,
				"Feil i forbindelse med sletting av vedlegg til søknad",
				"errorCode.backendError.attachmentDeleteError"
			)
		}

	fun saveFilDbData(innsendingsId: String, filDbData: FilDbData): FilDbData = try {
		filRepository.save(filDbData)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved lagring av filDbData for vedlegg ${filDbData.vedleggsid} til søknad $innsendingsId",
			"errorCode.backendError.fileSaveError"
		)
	}

	fun hentFilDb(innsendingsId: String, vedleggsId: Long, filId: Long): FilDbData = try {
		filRepository.findByVedleggsidAndId(vedleggsId, filId) ?: throw ResourceNotFoundException(
			"Feil ved henting av fil med id=$filId for søknad $innsendingsId",
			"errorCode.resourceNotFound.noFile"
		)
	} catch (ex: ResourceNotFoundException) {
		throw ex
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message, "Feil ved henting av fil med id=$filId for søknad $innsendingsId",
			"errorCode.backendError.fileFetchError"
		)
	}

	fun hentFilerTilVedlegg(innsendingsId: String, vedleggsId: Long): List<FilDbData> = try {
		filRepository.findAllByVedleggsid(vedleggsId)
	} catch (ex: Exception) {
		throw ResourceNotFoundException(
			ex.message,
			"Feil ved henting av filer for  vedlegg $vedleggsId til søknad $innsendingsId",
			"errorCode.resourceNotFound.noFiles"
		)
	}

	fun hentFilerTilVedleggUtenFilData(innsendingsId: String, vedleggsId: Long): List<FilDbData> = try {
		mapTilFilDbData(filWithoutDataRepository.findFilDbWIthoutFileDataByVedleggsid(vedleggsId))
	} catch (ex: Exception) {
		throw ResourceNotFoundException(
			ex.message,
			"Feil ved henting av filer for  vedlegg $vedleggsId til søknad $innsendingsId",
			"errorCode.resourceNotFound.noFiles"
		)
	}

	private fun mapTilFilDbData(filerUtenFilData: List<FilDbWithoutFileData>): List<FilDbData> {
		return filerUtenFilData.map {
			FilDbData(
				id = it.id, vedleggsid = it.vedleggsid, filnavn = it.filnavn, mimetype = it.mimetype,
				storrelse = it.storrelse, data = null, opprettetdato = it.opprettetdato
			)
		}.toList()
	}

	fun hentSumFilstorrelseTilVedlegg(innsendingsId: String, vedleggsId: Long): Long = try {
		filRepository.findSumByVedleggsid(vedleggsId) ?: 0L
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved henting av filer for  vedlegg $vedleggsId til søknad $innsendingsId",
			"errorCode.backendError.fileFetchError"
		)
	}

	fun slettFilerForVedlegg(vedleggsId: Long) =
		try {
			filRepository.deleteAllByVedleggsid(vedleggsId)
		} catch (ex: Exception) {
			throw BackendErrorException(
				ex.message,
				"Feil i forbindelse med sletting av filer til vedlegg $vedleggsId",
				"errorCode.backendError.fileDeleteError"
			)
		}

	fun slettFilDb(innsendingsId: String, vedleggsId: Long, filId: Long) = try {
		filRepository.deleteByVedleggsidAndId(vedleggsId, filId)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved sletting av fil til vedlegg $vedleggsId til søknad $innsendingsId",
			"errorCode.backendError.fileDeleteError"
		)
	}

	fun findAllByVedleggsid(innsendingsId: String, vedleggsId: Long): List<FilDbData> = try {
		filRepository.findAllByVedleggsid(vedleggsId)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved sletting av fil til vedlegg $vedleggsId til søknad $innsendingsId",
			"errorCode.backendError.attachmentFetchError"
		)
	}

	fun deleteAllBySoknadStatusAndInnsendtdato(eldreEnn: Int) = try {
		filRepository.deleteAllBySoknadStatusAndInnsendtdato(eldreEnn)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved sletting av filer til vedlegg på søknader eldre enn $eldreEnn dager",
			"errorCode.backendError.deleteFilesForOldApplicationsError"
		)
	}

	fun lagreHendelse(soknadDbData: SoknadDbData) {
		val hendelseType =
			if (soknadDbData.id == null) {
				HendelseType.Opprettet
			} else if (soknadDbData.status == SoknadsStatus.AutomatiskSlettet) {
				HendelseType.SlettetAvSystem
			} else if (soknadDbData.status == SoknadsStatus.Innsendt && soknadDbData.arkiveringsstatus == ArkiveringsStatus.IkkeSatt) {
				HendelseType.Innsendt
			} else if (soknadDbData.status == SoknadsStatus.Innsendt && soknadDbData.arkiveringsstatus == ArkiveringsStatus.Arkivert) {
				HendelseType.Arkivert
			} else if (soknadDbData.status == SoknadsStatus.Innsendt && soknadDbData.arkiveringsstatus == ArkiveringsStatus.ArkiveringFeilet) {
				HendelseType.ArkiveringFeilet
			} else if (soknadDbData.status == SoknadsStatus.Opprettet) {
				HendelseType.Endret // Opprettet og har en id
			} else if (soknadDbData.status == SoknadsStatus.Utfylt) {
				HendelseType.Utfylt
			} else {
				HendelseType.Ukjent
			}
		if (hendelseType == HendelseType.Ukjent) {
			logger.info("${soknadDbData.innsendingsid}: Ukjent hendelsetype, kun endring av f.eks. sistendretdato? Returnerer uten å registrere hendelsen")
			return
		}

		lagreHendelse(
			HendelseDbData(
				id = null,
				innsendingsid = soknadDbData.innsendingsid,
				hendelsetype = hendelseType,
				tidspunkt = LocalDateTime.now(),
				skjemanr = soknadDbData.skjemanr,
				tema = soknadDbData.tema,
				erettersending = soknadDbData.ettersendingsid != null
			)
		)

	}

	fun lagreHendelse(hendelseDbData: HendelseDbData) = try {
		hendelseRepository.save(hendelseDbData)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil i lagring av hendelse til søknad ${hendelseDbData.innsendingsid}",
			"errorCode.backendError.applicationSaveError"
		)
	}

	fun hentHendelse(innsendingsId: String, hendelseType: HendelseType? = null): List<HendelseDbData> = try {
		if (hendelseType == null) {
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(innsendingsId)
		} else {
			hendelseRepository.findAllByInnsendingsidAndHendelsetypeAndOrderByTidspunktDesc(innsendingsId, hendelseType)
		}
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil i henting av hendelse til søknad $innsendingsId",
			"errorCode.backendError.applicationFetchError"
		)
	}

}

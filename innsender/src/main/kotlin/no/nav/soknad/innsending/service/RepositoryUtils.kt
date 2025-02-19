package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.*
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
	private val hendelseRepository: HendelseRepository,
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun hentSoknadDb(id: Long): SoknadDbData = try {
		soknadRepository.findByIdOrNull(id) ?: throw ResourceNotFoundException("Fant ikke søknad med soknadsId $id")
	} catch (resourceNotFoundException: ResourceNotFoundException) {
		throw resourceNotFoundException
	} catch (ex: Exception) {
		throw BackendErrorException("Henting av søknad $id fra databasen feilet", ex)
	}

	fun hentSoknadDb(innsendingsId: String): SoknadDbData = try {
		soknadRepository.findByInnsendingsid(innsendingsId)
			?: throw ResourceNotFoundException("Fant ikke søknad med innsendingsid $innsendingsId")
	} catch (resourceNotFoundException: ResourceNotFoundException) {
		throw resourceNotFoundException
	} catch (ex: Exception) {
		throw BackendErrorException("Henting av søknad $innsendingsId fra databasen feilet", ex)
	}

	fun endreSoknadDb(id: Long, visningsSteg: Long) = try {
		soknadRepository.updateVisningsStegAndEndretDato(id, visningsSteg, LocalDateTime.now())
	} catch (ex: Exception) {
		throw BackendErrorException("Oppdatering av søknad med id= $id feilet", ex)
	}

	fun findAllByStatusesAndWithOpprettetdatoBefore(statuses: List<String>, opprettetFor: OffsetDateTime) = try {
		soknadRepository.findAllByStatusesAndWithOpprettetdatoBefore(statuses, opprettetFor)
	} catch (ex: Exception) {
		throw BackendErrorException(
			message = "Feil ved henting av alle soknader med status $statuses opprettet før $opprettetFor",
			cause = ex
		)
	}

	fun findAllByStatusesAndWithSkalSlettesDatoBefore(statuses: List<String>, dateCutOff: OffsetDateTime) = try {
		soknadRepository.findAllByStatusesAndWithSkalSlettesDatoBefore(statuses, dateCutOff)
	} catch (ex: Exception) {
		throw BackendErrorException(
			message = "Feil ved henting av alle soknader med status $statuses med skalslettesdato før $dateCutOff",
			cause = ex
		)
	}

	fun findAllByOpprettetdatoBefore(opprettetFor: OffsetDateTime) = try {
		soknadRepository.findAllByOpprettetdatoBefore(opprettetFor)
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved henting av alle soknader opprettet før $opprettetFor", ex)
	}

	fun finnAlleSoknaderGittBrukerIdOgStatus(brukerId: String, status: SoknadsStatus) = try {
		soknadRepository.findByBrukeridAndStatus(brukerId, status)
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved henting av alle soknader for bruker xxxx med status $status", ex)
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
				message = "Feil ved henting av alle arkiverte søknader arkivert mellom ${(vindu + eldreEnn)} og $eldreEnn dager siden",
				cause = ex
			)
		}

	fun lagreSoknad(soknadDbData: SoknadDbData): SoknadDbData {
		try {
			lagreHendelse(soknadDbData)
			val savedSoknad = soknadRepository.save(soknadDbData)
			logger.info("Lagret søknad med status: ${savedSoknad.status}, skjemanr: ${savedSoknad.skjemanr}, innsendingsId: ${savedSoknad.innsendingsid} type: ${savedSoknad.visningstype}, applikasjon: ${savedSoknad.applikasjon}")
			return savedSoknad
		} catch (ex: Exception) {
			throw BackendErrorException("Feil i lagring av søknad ${soknadDbData.tittel}", ex)
		}
	}

	fun slettSoknad(dokumentSoknadDto: DokumentSoknadDto, hendelseType: HendelseType): HendelseDbData = try {
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
		throw BackendErrorException("Feil ved sletting av søknad ${dokumentSoknadDto.innsendingsId}", ex)
	}

	fun oppdaterEndretDato(soknadsId: Long) = try {
		soknadRepository.updateEndretDato(soknadsId, LocalDateTime.now())
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved oppdatering av søknad med id $soknadsId", ex)
	}

	fun oppdaterArkiveringsstatus(soknadDbData: SoknadDbData, arkiveringsStatus: ArkiveringsStatus): HendelseDbData =
		try {
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
				message = "Feil ved oppdatering av arkiveringsstatus på søknad med innsendingsId ${soknadDbData.innsendingsid}",
				cause = ex,
			)
		}


	fun endreTema(id: Long, innsendingsid: String, tema: String) {
		try {
			soknadRepository.updateTema(id, tema)
		} catch (ex: Exception) {
			throw BackendErrorException(
				message = "Feil ved oppdatering av tema på søknad med innsendingsId $innsendingsid",
				cause = ex,
			)
		}
	}

	fun findNumberOfEventsByType(hendelseType: HendelseType): Long? = try {
		hendelseRepository.countByHendelsetype(hendelseType)
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved henting av antall hendelser gitt hendelsetype $hendelseType", ex)
	}


	fun hentVedlegg(vedleggsId: Long): VedleggDbData = try {
		vedleggRepository.findByVedleggsid(vedleggsId)
			?: throw ResourceNotFoundException("Fant ikke vedlegg med id $vedleggsId")
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved forsøk på henting av vedlegg med id $vedleggsId", ex)
	}

	fun hentAlleVedleggGittSoknadsid(soknadsId: Long): List<VedleggDbData> = try {
		vedleggRepository.findAllBySoknadsid(soknadsId)
			.filter {it.vedleggsnr != "N6" || (it.vedleggsnr=="N6" && !(it.label.equals("Annen dokumentasjon") || it.label.equals("Annan dokumentasjon") || it.label.equals("Other documentation")))}
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved forsøk på henting av alle vedlegg til søknad med id $soknadsId", ex)
	}

	fun lagreVedlegg(vedleggDbData: VedleggDbData): VedleggDbData {
		try {
			val savedVedlegg = vedleggRepository.save(vedleggDbData)
			logger.info("Lagret vedlegg soknadsId: ${savedVedlegg.soknadsid}, vedleggsnr: ${savedVedlegg.vedleggsnr}, status: ${savedVedlegg.status}, formioId: ${savedVedlegg.formioid}")
			return savedVedlegg
		} catch (ex: Exception) {
			throw BackendErrorException("Feil i lagring av vedleggsdata ${vedleggDbData.vedleggsnr} til søknad", ex)
		}
	}

	fun oppdaterVedlegg(innsendingsId: String, vedleggDbData: VedleggDbData): VedleggDbData = try {
		vedleggRepository.save(vedleggDbData)
		vedleggRepository.findByVedleggsid(vedleggDbData.id!!)
			?: throw ResourceNotFoundException("Fant ikke vedlegg med id ${vedleggDbData.id}")
	} catch (ex: ResourceNotFoundException) {
		throw ex
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved oppdatering av vedlegg ${vedleggDbData.id} for søknad $innsendingsId", ex)
	}

	fun updateVedleggStatus(
		innsendingsId: String,
		vedleggsId: Long,
		opplastingsStatus: OpplastingsStatus
	): Int = try {
		vedleggRepository.updateStatus(id = vedleggsId, status = opplastingsStatus, endretdato = LocalDateTime.now())
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved oppdatering av status for vedlegg $vedleggsId for søknad $innsendingsId", ex)
	}

	fun oppdaterVedleggStatusOgInnsendtdato(
		innsendingsId: String, vedleggsId: Long, opplastingsStatus: OpplastingsStatus,
		endretDato: LocalDateTime, innsendtDato: LocalDateTime?
	): Int = try {
		logger.info("oppdaterVedleggStatusOgInnsendtdato: vedlegg=$vedleggsId, innsendtdato=$innsendtDato, opplastingsStatus=$opplastingsStatus")
		val raderEndret = vedleggRepository.updateStatusAndInnsendtdato(
			id = vedleggsId, status = opplastingsStatus, endretdato = endretDato, innsendtdato = innsendtDato
		)
		if (raderEndret != 1) {
			logger.error("$innsendingsId: oppdaterVedleggStatusOgInnsendtdato: uventet antall, $raderEndret, rader endret for vedlegg $vedleggsId")
		}
		raderEndret
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved oppdatering av status for vedlegg $vedleggsId for søknad $innsendingsId", ex)
	}

	fun updateVedleggErPakrevd(
		vedleggsId: Long,
		erPakrevd: Boolean
	): Int = try {
		vedleggRepository.updateErPakrevd(id = vedleggsId, erpakrevd = erPakrevd, endretdato = LocalDateTime.now())
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved oppdatering av status for vedlegg $vedleggsId", ex)
	}

	fun slettVedlegg(vedleggsId: Long) {
		try {
			vedleggRepository.deleteById(vedleggsId)
			logger.info("Slettet vedlegg med id $vedleggsId")
		} catch (ex: Exception) {
			throw BackendErrorException("Feil i forbindelse med sletting av vedlegg til søknad", ex)
		}
	}


	fun saveFilDbData(innsendingsId: String, filDbData: FilDbData): FilDbData {
		try {
			val fil = filRepository.save(filDbData)
			logger.info("Lagret fil med vedleggsid ${fil.vedleggsid}")
			return fil
		} catch (ex: Exception) {
			throw BackendErrorException(
				message = "Feil ved lagring av filDbData for vedlegg ${filDbData.vedleggsid} til søknad $innsendingsId",
				cause = ex
			)
		}
	}

	fun hentFilDb(innsendingsId: String, vedleggsId: Long, filId: Long): FilDbData = try {
		filRepository.findByVedleggsidAndId(vedleggsId, filId)
			?: throw ResourceNotFoundException("Feil ved henting av fil med id=$filId for søknad $innsendingsId")
	} catch (ex: ResourceNotFoundException) {
		throw ex
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved henting av fil med id=$filId for søknad $innsendingsId", ex)
	}

	fun hentFilerTilVedlegg(innsendingsId: String, vedleggsId: Long): List<FilDbData> = try {
		filRepository.findAllByVedleggsid(vedleggsId)
	} catch (ex: Exception) {
		throw ResourceNotFoundException("Feil ved henting av filer for  vedlegg $vedleggsId til søknad $innsendingsId", ex)
	}

	fun hentFilerTilVedleggUtenFilData(innsendingsId: String, vedleggsId: Long): List<FilDbData> = try {
		mapTilFilDbData(filWithoutDataRepository.findFilDbWIthoutFileDataByVedleggsid(vedleggsId))
	} catch (ex: Exception) {
		throw ResourceNotFoundException("Feil ved henting av filer for  vedlegg $vedleggsId til søknad $innsendingsId", ex)
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
		throw BackendErrorException("Feil ved henting av filer for  vedlegg $vedleggsId til søknad $innsendingsId", ex)
	}

	fun slettFilerForVedlegg(vedleggsId: Long) =
		try {
			filRepository.deleteAllByVedleggsid(vedleggsId)
		} catch (ex: Exception) {
			throw BackendErrorException("Feil i forbindelse med sletting av filer til vedlegg $vedleggsId", ex)
		}

	fun slettFilDb(innsendingsId: String, vedleggsId: Long, filId: Long) = try {
		filRepository.deleteByVedleggsidAndId(vedleggsId, filId)
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved sletting av fil til vedlegg $vedleggsId til søknad $innsendingsId", ex)
	}

	fun findAllByVedleggsid(innsendingsId: String, vedleggsId: Long): List<FilDbData> = try {
		filRepository.findAllByVedleggsid(vedleggsId)
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved henting av filer til vedlegg $vedleggsId for søknad $innsendingsId", ex)
	}

	fun deleteAllBySoknadStatusAndInnsendtdato(eldreEnn: Int) = try {
		filRepository.deleteAllBySoknadStatusAndInnsendtdato(eldreEnn)
	} catch (ex: Exception) {
		throw BackendErrorException("Feil ved sletting av filer til vedlegg på søknader eldre enn $eldreEnn dager", ex)
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

	fun lagreHendelse(hendelseDbData: HendelseDbData): HendelseDbData = try {
		hendelseRepository.save(hendelseDbData)
	} catch (ex: Exception) {
		throw BackendErrorException("Feil i lagring av hendelse til søknad ${hendelseDbData.innsendingsid}", ex)
	}

	fun hentHendelse(innsendingsId: String, hendelseType: HendelseType? = null): List<HendelseDbData> = try {
		if (hendelseType == null) {
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(innsendingsId)
		} else {
			hendelseRepository.findAllByInnsendingsidAndHendelsetypeAndOrderByTidspunktDesc(innsendingsId, hendelseType)
		}
	} catch (ex: Exception) {
		throw BackendErrorException("Feil i henting av hendelse til søknad $innsendingsId", ex)
	}

}

package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.repository.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

@Service
class RepositoryUtils(
	private val soknadRepository: SoknadRepository,
	private val vedleggRepository: VedleggRepository,
	private val filRepository: FilRepository
) {
	fun hentSoknadDb(id: Long): Optional<SoknadDbData> = try {
		soknadRepository.findById(id)
	} catch (re: Exception) {
		throw BackendErrorException(re.message, "Henting av søknad $id fra databasen feilet")
	}

	fun hentSoknadDb(innsendingsId: String): Optional<SoknadDbData> = try {
		soknadRepository.findByInnsendingsid(innsendingsId)
	} catch (re: Exception) {
		throw BackendErrorException(re.message, "Henting av søknad $innsendingsId fra databasen feilet")
	}

	fun endreSoknadDb(id: Long, visningsSteg: Long) = try {
		soknadRepository.updateVisningsStegAndEndretDato(id, visningsSteg, LocalDateTime.now())
	} catch (re: Exception) {
		throw BackendErrorException(re.message, "Oppdatering av søknad med id= $id feilet")
	}

	fun findAllByStatusAndWithOpprettetdatoBefore(status: String, opprettetFor: OffsetDateTime) = try {
		soknadRepository.findAllByStatusAndWithOpprettetdatoBefore(status, opprettetFor)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved henting av alle soknader med status $status opprettet før $opprettetFor")
	}

	fun finnAlleSoknaderGittBrukerIdOgStatus(brukerId: String, status: SoknadsStatus) = try {
		soknadRepository.findByBrukeridAndStatus(brukerId, status)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved henting av alle soknader for bruker xxxx med status $status")
	}

	fun finnNyesteSoknadGittEttersendingsId(ettersendingsId: String): List<SoknadDbData> = try {
		soknadRepository.findNewestByEttersendingsId(ettersendingsId)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved henting av nyeste søknad gitt ettersendingsid $$ettersendingsId")
	}

	fun lagreSoknad(soknadDbData: SoknadDbData) = try {
		soknadRepository.save(soknadDbData)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil i lagring av søknad ${soknadDbData.tittel}")
	}

	fun soknadSaveAndFlush(soknadDbData: SoknadDbData)  = try {
		soknadRepository.saveAndFlush(soknadDbData)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved lagring og flush av søknad ${soknadDbData.innsendingsid}")
	}

	fun slettSoknad(dokumentSoknadDto: DokumentSoknadDto) = try {
		soknadRepository.deleteById(dokumentSoknadDto.id!!)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved sletting av søknad ${dokumentSoknadDto.innsendingsId}")
	}

	fun oppdaterEndretDato(soknadsId: Long) = try {
		soknadRepository.updateEndretDato(soknadsId, LocalDateTime.now())
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved oppdatering av søknad med id $soknadsId")
	}

	fun hentVedlegg(vedleggsId: Long): Optional<VedleggDbData> = try {
		vedleggRepository.findByVedleggsid(vedleggsId)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved forsøk på henting av vedlegg med id $vedleggsId")
	}

	fun hentAlleVedleggGittSoknadsid(soknadsId: Long): List<VedleggDbData> = try {
		vedleggRepository.findAllBySoknadsid(soknadsId)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved forsøk på henting av alle vedlegg til søknad med id $soknadsId")

	}

	fun lagreVedlegg(vedleggDbData: VedleggDbData) = try {
		vedleggRepository.save(vedleggDbData)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil i lagring av vedleggsdata ${vedleggDbData.vedleggsnr} til søknad")
	}

	fun flushVedlegg() = try {
		vedleggRepository.flush()
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved flush av vedlegg")
	}

	fun saveVedlegg(vedleggDbData: VedleggDbData) = try {
		vedleggRepository.save(vedleggDbData)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved save av vedlegg ${vedleggDbData.vedleggsnr} på søknadsid ${vedleggDbData.soknadsid}")
	}

	fun oppdaterVedlegg(innsendingsId: String, vedleggDbData: VedleggDbData): Optional<VedleggDbData> = try {
		vedleggRepository.save(vedleggDbData)
		vedleggRepository.findByVedleggsid(vedleggDbData.id!!)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved oppdatering av vedlegg ${vedleggDbData.id} for søknad $innsendingsId"
		)
	}

	fun oppdaterVedleggStatus(innsendingsId: String, vedleggsId: Long, opplastingsStatus: OpplastingsStatus, localDateTime: LocalDateTime) = try {
		if (opplastingsStatus != OpplastingsStatus.INNSENDT)
			vedleggRepository.updateStatus(vedleggsId, opplastingsStatus, localDateTime)
		else
			vedleggRepository.updateStatusAndInnsendtdato(vedleggsId, opplastingsStatus, localDateTime, LocalDateTime.now())
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved oppdatering av status for vedlegg $vedleggsId for søknad $innsendingsId"
		)
	}

	fun oppdaterVedleggsTittelOgLabelOgStatus(
		vedleggDbData: VedleggDbData,
		nyTittel: String?,
		nyVedleggsStatus: OpplastingsStatus?
	): Optional<VedleggDbData> = try {
		vedleggRepository.patchVedlegg(
			vedleggDbData.id!!, nyTittel ?: vedleggDbData.tittel,
			nyVedleggsStatus ?: vedleggDbData.status, LocalDateTime.now()
		)
		vedleggRepository.findByVedleggsid(vedleggDbData.id)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved oppdatering av vedlegg ${vedleggDbData.id} for søknad ${vedleggDbData.soknadsid}"
		)
	}

	fun slettVedlegg(vedleggsId: Long) =
		try {
			vedleggRepository.deleteById(vedleggsId)
		} catch (ex: Exception) {
			throw BackendErrorException(ex.message, "Feil i forbindelse med sletting av vedlegg til søknad")
		}

	fun saveFilDbData(innsendingsId: String, filDbData: FilDbData) = try {
		filRepository.save(filDbData)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved lagring av filDbData for vedlegg ${filDbData.vedleggsid} til søknad $innsendingsId")
	}

	fun hentFilDb(innsendingsId: String, vedleggsId: Long, filId: Long): Optional<FilDbData> = try {
		filRepository.findByVedleggsidAndId(vedleggsId, filId)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved henting av fil med id=$filId for søknad $innsendingsId")
	}

	fun hentFilerTilVedlegg(innsendingsId: String, vedleggsId: Long): List<FilDbData> = try {
		filRepository.findAllByVedleggsid(vedleggsId)
	} catch (ex: Exception) {
		throw ResourceNotFoundException(
			ex.message,
			"Feil ved henting av filer for  vedlegg $vedleggsId til søknad $innsendingsId"
		)
	}

	fun slettFilerForVedlegg(vedleggsId: Long) =
		try {
			filRepository.deleteAllByVedleggsid(vedleggsId)
		} catch (ex: Exception) {
			throw BackendErrorException(ex.message, "Feil i forbindelse med sletting av filer til vedlegg $vedleggsId")
		}

	fun slettFilDb(innsendingsId: String, vedleggsId: Long, filId: Long) = try {
		filRepository.deleteByVedleggsidAndId(vedleggsId, filId)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved sletting av fil til vedlegg $vedleggsId til søknad $innsendingsId"
		)
	}

	fun findAllByVedleggsid(innsendingsId: String, vedleggsId: Long): List<FilDbData> = try {
		filRepository.findAllByVedleggsid(vedleggsId)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved sletting av fil til vedlegg $vedleggsId til søknad $innsendingsId"
		)
	}

	fun deleteAllBySoknadStatusAndInnsendtdato(eldreEnn: Int)  = try {
		filRepository.deleteAllBySoknadStatusAndInnsendtdato(eldreEnn)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil ved sletting av filer til vedlegg på søknader eldre enn $eldreEnn dager"
		)
	}
}

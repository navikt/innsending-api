package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.skjema.KodeverkSkjema
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerAPI
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.SoknadsmottakerAPI
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.FilDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.exceptions.SanityException
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.pdfutilities.PdfGenerator
import no.nav.soknad.pdfutilities.PdfMerger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class SoknadService(
	private val skjemaService: HentSkjemaDataConsumer,
	private val soknadRepository: SoknadRepository,
	private val vedleggRepository: VedleggRepository,
	private val filRepository: FilRepository,
	private val brukerNotifikasjon: BrukernotifikasjonPublisher,
	private val fillagerAPI: FillagerAPI,
	private val soknadsmottakerAPI: SoknadsmottakerAPI
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	private val ukjentEttersendingsId = "-1"

	@Transactional
	fun opprettSoknad(brukerId: String, skjemanr: String, spraak: String = "no", vedleggsnrListe: List<String> = emptyList()): DokumentSoknadDto {
		// hentSkjema informasjon gitt skjemanr
		val kodeverkSkjema = hentSkjema(skjemanr, spraak)

		// lagre soknad
		val savedSoknadDbData = lagreSoknad(
				SoknadDbData(null, Utilities.laginnsendingsId(), kodeverkSkjema.tittel ?: "", kodeverkSkjema.skjemanummer ?: "",
					kodeverkSkjema.tema ?: "", spraak, SoknadsStatus.Opprettet, brukerId, null, LocalDateTime.now(),
					LocalDateTime.now(), null)
			)

		// Lagre skjema som hovedvedlegg
		val skjemaDbData = lagreVedlegg(
				VedleggDbData(null, savedSoknadDbData.id!!, OpplastingsStatus.IKKE_VALGT, true, ervariant = false, true
					, kodeverkSkjema.skjemanummer ?: kodeverkSkjema.vedleggsid,kodeverkSkjema.tittel ?: "",
					null, UUID.randomUUID().toString(), LocalDateTime.now(), LocalDateTime.now(), kodeverkSkjema.url)
			)

		// for hvert vedleggsnr hent fra Sanity og opprett vedlegg.
		val vedleggDbDataListe = vedleggsnrListe
			.map { nr -> hentSkjema(nr, spraak) }
			.map { v ->  lagreVedlegg(VedleggDbData(null, savedSoknadDbData.id, OpplastingsStatus.IKKE_VALGT,
				 false, ervariant = false, false, v.skjemanummer,v.tittel ?: "", null,
				UUID.randomUUID().toString(), LocalDateTime.now(), LocalDateTime.now(), v.url)) }

		val savedVedleggDbDataListe = listOf(skjemaDbData) + vedleggDbDataListe

		val dokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbDataListe)
		publiserBrukernotifikasjon(dokumentSoknadDto)

		// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
		return dokumentSoknadDto
	}

	private fun hentSkjema(nr: String, spraak: String) =  try {
		skjemaService.hentSkjemaEllerVedlegg(nr, spraak)
	} catch (re: SanityException) {
		throw ResourceNotFoundException(re.arsak, re.message ?: "")
	}

	private fun hentSoknadDb(id: Long): Optional<SoknadDbData> =  try {
		soknadRepository.findById(id)
	} catch (re: Exception) {
		throw BackendErrorException(re.message, "Henting av søknad $id fra databasen feilet")
	}

	private fun hentSoknadDb(innsendingsId: String): Optional<SoknadDbData> =  try {
		soknadRepository.findByInnsendingsid(innsendingsId)
	} catch (re: Exception) {
		throw BackendErrorException(re.message, "Henting av søknad $innsendingsId fra databasen feilet")
	}

	private fun hentVedlegg(vedleggsId: Long): Optional<VedleggDbData> = try {
		vedleggRepository.findByVedleggsid(vedleggsId)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved forsøk på henting av vedlegg med id $vedleggsId")
	}

	private fun hentFilDb(innsendingsId: String, vedleggsId: Long, filId: Long): Optional<FilDbData> = try {
		filRepository.findByVedleggsidAndId(vedleggsId, filId)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved henting av fil med id=$filId for søknad $innsendingsId")
	}

	private fun hentFilerTilVedlegg(innsendingsId: String, vedleggsId: Long): List<FilDbData> = try {
		filRepository.findAllByVedleggsid(vedleggsId)
	} catch (ex: Exception) {
		throw ResourceNotFoundException(ex.message, "Feil ved henting av filer for  vedlegg $vedleggsId til søknad $innsendingsId")
	}

	private fun lagreSoknad(soknadDbData: SoknadDbData) = try {
		soknadRepository.save(soknadDbData)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil i lagring av søknad ${soknadDbData.tittel}")
	}

	private fun lagreVedlegg(vedleggDbData: VedleggDbData) = try {
		vedleggRepository.save(vedleggDbData)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil i lagring av vedleggsdata ${vedleggDbData.vedleggsnr} til søknad")
	}

	private fun oppdaterVedlegg(innsendingsId: String, vedleggDbData: VedleggDbData) = try {
		vedleggRepository.save(vedleggDbData)
	} catch (ex: Exception) {
		BackendErrorException(ex.message, "Feil ved oppdatering av vedlegg ${vedleggDbData.id} for søknad $innsendingsId")
	}

	private fun oppdaterEndretDato(soknadsId: Long) = try {
		soknadRepository.updateEndretDato(soknadsId, LocalDateTime.now())
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved oppdatering av søknad med id $soknadsId" )
	}

	private fun slettSoknad(dokumentSoknadDto: DokumentSoknadDto) = try {
		soknadRepository.deleteById(dokumentSoknadDto.id!!)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved sletting av søknad ${dokumentSoknadDto.innsendingsId}")
	}

	private fun slettVedlegg(vedleggsId: Long) =
		try {
			vedleggRepository.deleteById(vedleggsId)
		} catch (ex: Exception) {
			throw BackendErrorException(ex.message, "Feil i forbindelse med sletting av vedlegg til søknad")
		}

	private fun slettFilerForVedlegg(vedleggsId: Long) =
		try {
			filRepository.deleteFilDbDataForVedlegg(vedleggsId)
		} catch (ex: Exception) {
			throw BackendErrorException(ex.message, "Feil i forbindelse med sletting av filer til søknad")
		}

	private fun slettFilDb(innsendingsId: String, vedleggsId: Long, filId: Long) = try {
		filRepository.deleteByVedleggsidAndId(vedleggsId, filId)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil ved sletting av fil til vedlegg $vedleggsId til søknad $innsendingsId")
	}

	private fun publiserBrukernotifikasjon(dokumentSoknadDto: DokumentSoknadDto): Boolean = try {
		brukerNotifikasjon.soknadStatusChange(dokumentSoknadDto)
	} catch (ex: Exception) {
		throw BackendErrorException(ex.message, "Feil i lagring av informasjon om søknad ${dokumentSoknadDto.tittel} til Ditt NAV")
	}

	@Transactional
	fun opprettSoknadForEttersendingGittSkjemanr(brukerId: String, skjemanr: String, spraak: String = "no", vedleggsnrListe: List<String> = emptyList()): DokumentSoknadDto {
		// hentSkjema informasjon gitt skjemanr
		val kodeverkSkjema = hentSkjema(skjemanr, spraak)

		// lagre soknad og vedlegg
		val savedSoknadDbData = lagreSoknad(
			SoknadDbData(null, Utilities.laginnsendingsId(), kodeverkSkjema.tittel ?: "", kodeverkSkjema.skjemanummer ?: "",
				kodeverkSkjema.tema ?: "", spraak, SoknadsStatus.Opprettet, brukerId, ukjentEttersendingsId, LocalDateTime.now(),
				LocalDateTime.now(), null)
		)

		// Lagre skjema
		val skjemaDbData = lagreVedlegg(
			VedleggDbData(null, savedSoknadDbData.id!!, OpplastingsStatus.INNSENDT, true, ervariant = false, true
				, kodeverkSkjema.skjemanummer ?: kodeverkSkjema.vedleggsid,kodeverkSkjema.tittel ?: "",
				null, UUID.randomUUID().toString(), LocalDateTime.now(), LocalDateTime.now(), kodeverkSkjema.url)
		)

		// for hvert vedleggsnr hent fra Sanity og opprett vedlegg.
		val vedleggDbDataListe = vedleggsnrListe
			.map { nr -> hentSkjema(nr, spraak) }
			.map { v ->  lagreVedlegg(VedleggDbData(null, savedSoknadDbData.id, OpplastingsStatus.IKKE_VALGT,
				false, ervariant = false, false, v.skjemanummer,v.tittel ?: "", null,
				UUID.randomUUID().toString(), LocalDateTime.now(), LocalDateTime.now(), v.url)) }

		val savedVedleggDbDataListe = listOf(skjemaDbData) + vedleggDbDataListe

		val dokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbDataListe)
		publiserBrukernotifikasjon(dokumentSoknadDto)
		// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
		return dokumentSoknadDto
	}

	fun opprettSoknadForettersendingAvVedlegg(brukerId: String, ettersendingsId: String): DokumentSoknadDto {
		// Skal opprette en soknad basert på status på vedlegg som skal ettersendes.
		// Basere opplastingsstatus på nyeste innsending på ettersendingsId, dvs. nyeste soknad der innsendingsId eller ettersendingsId lik oppgitt ettersendingsId
		// Det skal være mulig å ettersende allerede ettersendte vedlegg på nytt
		val soknadDbDataList = try {
			soknadRepository.findNewestByEttersendingsId(ettersendingsId)
		} catch (ex: Exception) {
			throw BackendErrorException(ex.message, "Feil ved henting av søknad ${ettersendingsId}")
		}

		if (soknadDbDataList.isEmpty())
			throw ResourceNotFoundException("Kan ikke opprette søknad for ettersending", "Soknad med id $ettersendingsId som det skal ettersendes data for ble ikke funnet")

		val nyesteSoknad = hentAlleVedlegg(soknadDbDataList.first())

		val savedEttersendingsSoknad = lagreSoknad(
			SoknadDbData(null, Utilities.laginnsendingsId(), nyesteSoknad.tittel, nyesteSoknad.skjemanr,
				nyesteSoknad.tema, nyesteSoknad.spraak, SoknadsStatus.Opprettet, brukerId, ettersendingsId, LocalDateTime.now(),
				LocalDateTime.now(), null)
		)

		val vedleggDbDataListe = nyesteSoknad.vedleggsListe
			.map { v -> lagreVedlegg(VedleggDbData(null, savedEttersendingsSoknad.id!!
				, if (OpplastingsStatus.SEND_SENERE == v.opplastingsStatus) OpplastingsStatus.IKKE_VALGT else v.opplastingsStatus,
				v.erHoveddokument, v.erVariant, v.erPdfa, v.vedleggsnr,v.tittel, v.mimetype,
				UUID.randomUUID().toString(), LocalDateTime.now(), LocalDateTime.now()
				, if (v.vedleggsnr != null) hentSkjema(v.vedleggsnr, nyesteSoknad.spraak ?: "no" ).url else null)) }

		val dokumentSoknadDto = lagDokumentSoknadDto(savedEttersendingsSoknad, vedleggDbDataListe)
		publiserBrukernotifikasjon(dokumentSoknadDto)

		return dokumentSoknadDto
	}

	@Transactional
	fun opprettEllerOppdaterSoknad(dokumentSoknadDto: DokumentSoknadDto): String {
		val innsendingsId = dokumentSoknadDto.innsendingsId ?: Utilities.laginnsendingsId()
		// Hvis soknad ikke eksisterer må den lagres, før vedleggene
		val savedSoknadDbData = lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId))
		val soknadsid = savedSoknadDbData.id
		val savedVedleggDbData = dokumentSoknadDto.vedleggsListe
			.map { lagreVedlegg(mapTilVedleggDb(it, soknadsid!!, skjemaService.hentSkjemaEllerVedlegg(it.vedleggsnr!!, savedSoknadDbData.spraak))) }

		val savedDokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbData)
		publiserBrukernotifikasjon(savedDokumentSoknadDto)

		return innsendingsId
	}


	// Hent soknad gitt id med alle vedlegg. Merk at eventuelt dokument til vedlegget hentes ikke
	fun hentSoknad(id: Long): DokumentSoknadDto {
		val soknadDbDataOpt = hentSoknadDb(id)
		return hentAlleVedlegg(soknadDbDataOpt, id.toString())
	}

	// Hent soknad gitt innsendingsid med alle vedlegg. Merk at eventuelt dokument til vedlegget hentes ikke
	fun hentSoknad(innsendingsId: String): DokumentSoknadDto {
		val soknadDbDataOpt = hentSoknadDb(innsendingsId)
		return hentAlleVedlegg(soknadDbDataOpt, innsendingsId)
	}

	private fun hentAlleVedlegg(soknadDbDataOpt: Optional<SoknadDbData>, ident: String): DokumentSoknadDto {
		if (soknadDbDataOpt.isPresent) {
			return hentAlleVedlegg(soknadDbDataOpt.get())
		}
		throw ResourceNotFoundException(null, "Ingen soknad med id = $ident funnet")
	}

	private fun hentAlleVedlegg(soknadDbData: SoknadDbData): DokumentSoknadDto {
		val vedleggDbDataListe = try {
			vedleggRepository.findAllBySoknadsid(soknadDbData.id!!)
		} catch (ex: Exception) {
			throw ResourceNotFoundException("Ved oppretting av søknad skal det minimum være opprettet et vedlegg for selve søknaden", "Fant ingen vedlegg til soknad ${soknadDbData.innsendingsid}")
		}
		return lagDokumentSoknadDto(soknadDbData, vedleggDbDataListe)
	}

	// Hent vedlegg, merk filene knyttet til vedlegget ikke lastes opp
	fun hentVedleggDto(vedleggsId: Long): VedleggDto  {
		val vedleggDbDataOpt = hentVedlegg(vedleggsId)
		if (!vedleggDbDataOpt.isPresent)
			throw ResourceNotFoundException(null, "Vedlegg med id $vedleggsId ikke funnet")

		return lagVedleggDto(vedleggDbDataOpt.get())
	}

	@Transactional
	fun lagreFil(soknadDto: DokumentSoknadDto, filDto: FilDto): FilDto {
		// TODO Valider opplastet fil, og konverter eventuelt til PDF
		if (soknadDto.status != SoknadsStatus.Opprettet) {
			when (soknadDto.status) {
				SoknadsStatus.Innsendt -> throw IllegalActionException(
					"Innsendte søknader kan ikke endres. Ønsker søker å gjøre oppdateringer, så må vedkommende ettersende dette",
					"Søknad ${soknadDto.innsendingsId} er sendt inn og opplastede filer er ikke tilgjengelig her. Gå til Ditt Nav og søk opp dine saker der")
				SoknadsStatus.SlettetAvBruker, SoknadsStatus.AutomatiskSlettet -> throw IllegalActionException(
					"Søknader markert som slettet kan ikke endres. Søker må eventuelt opprette ny søknad",
					"Søknaden er slettet og ingen filer er tilgjengelig")
				else -> {} // Do nothing
			}
		}

		if (soknadDto.vedleggsListe.filter {it.id == filDto.vedleggsid }.isEmpty())
			throw ResourceNotFoundException(null, "Vedlegg $filDto.vedleggsid til søknad ${soknadDto.innsendingsId} eksisterer ikke")

		val savedFilDbData = try {
			filRepository.save(mapTilFilDb(filDto))
		} catch (ex: Exception) {
			throw BackendErrorException(ex.message, "Feil ved lagring av fil til på vedlegg ${filDto.vedleggsid} søknad ${soknadDto.innsendingsId}")
		}
/* Oppdaterer kun opplastingsstatus i forbindelse med innsending av søknad.
		if (vedleggDbData.status != OpplastingsStatus.LASTET_OPP) {
			vedleggRepository.updateStatus(vedleggDbData.id!!, OpplastingsStatus.LASTET_OPP, LocalDateTime.now())
		}
*/
		return lagFilDto(savedFilDbData)
	}

	fun hentFil(soknadDto: DokumentSoknadDto, vedleggsId: Long, filId: Long): FilDto {
		// Sjekk om vedlegget eksisterer
		if (soknadDto.status != SoknadsStatus.Opprettet) {
			when (soknadDto.status) {
				SoknadsStatus.Innsendt -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknad ${soknadDto.innsendingsId} er sendt inn og opplastede filer er ikke tilgjengelig her. Gå til Ditt Nav og søk opp dine saker der")
				SoknadsStatus.SlettetAvBruker, SoknadsStatus.AutomatiskSlettet -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknaden er slettet og ingen filer er tilgjengelig")
				else -> {} // Do nothing
			}
		}
		if (soknadDto.vedleggsListe.filter {it.id == vedleggsId }.isEmpty())
			throw ResourceNotFoundException(null, "Vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke")

		val filDbDataOpt = hentFilDb(soknadDto.innsendingsId!!, vedleggsId, filId)

		if (!filDbDataOpt.isPresent)
			throw ResourceNotFoundException(null, "Det finnes ikke fil med id=$filId for søknad ${soknadDto.innsendingsId}")

		return lagFilDto(filDbDataOpt.get())
	}

	fun hentFiler(soknadDto: DokumentSoknadDto, innsendingsId: String, vedleggsId: Long, medFil: Boolean = false): List<FilDto> {
		return hentFiler(soknadDto, innsendingsId, vedleggsId, medFil, true)
	}

	fun hentFiler(soknadDto: DokumentSoknadDto, innsendingsId: String, vedleggsId: Long, medFil: Boolean = false, kastFeilNarNull: Boolean = true): List<FilDto> {
		// Sjekk om vedlegget eksisterer for soknad og at soknaden er i status opprettet
		if (soknadDto.status != SoknadsStatus.Opprettet) {
			when (soknadDto.status) {
				SoknadsStatus.Innsendt -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknad $innsendingsId er sendt inn og opplastede filer er ikke tilgjengelig her. Gå til Ditt Nav og søk opp dine saker der")
				SoknadsStatus.SlettetAvBruker, SoknadsStatus.AutomatiskSlettet -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknaden er slettet og ingen filer er tilgjengelig")
				else -> {} // Do nothing
			}
		}
		if (soknadDto.vedleggsListe.filter {it.id == vedleggsId }.isEmpty())
			throw ResourceNotFoundException(null, "Vedlegg $vedleggsId til søknad $innsendingsId eksisterer ikke")

		val filDbDataList = hentFilerTilVedlegg(innsendingsId, vedleggsId)
		if (filDbDataList.isEmpty() && kastFeilNarNull )
			throw ResourceNotFoundException(null, "Ingen filer funnet for oppgitt vedlegg $vedleggsId til søknad $innsendingsId")

		return filDbDataList.map { lagFilDto(it, medFil) }
	}

	fun slettFil(soknadDto: DokumentSoknadDto, vedleggsId: Long, filId: Long) {
		// Sjekk og vedlegget eksisterer
		if (soknadDto.status != SoknadsStatus.Opprettet) {
			when (soknadDto.status) {
				SoknadsStatus.Innsendt -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknad ${soknadDto.innsendingsId} er sendt inn og kan ikke endres.")
				SoknadsStatus.SlettetAvBruker, SoknadsStatus.AutomatiskSlettet -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknaden er slettet og ingen filer er tilgjengelig")
				else -> {} // Do nothing
			}
		}
		if (soknadDto.vedleggsListe.filter {it.id == vedleggsId }.isEmpty())
			throw ResourceNotFoundException(null, "Vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke")

		slettFilDb(soknadDto.innsendingsId!!, vedleggsId, filId)
/* Setter opplastingsstatus på vedlegg kun i forbindelse med innsending av søknad
		if (filRepository.findNumberOfFilesByVedleggsid(vedleggDbData.id!!) == 0) {
			vedleggRepository.updateStatus(vedleggDbData.id!!, OpplastingsStatus.IKKE_VALGT, LocalDateTime.now())
		}
*/
	}

	// Slett opprettet soknad gitt innsendingsId
	@Transactional
	fun slettSoknadAvBruker(dokumentSoknadDto: DokumentSoknadDto) {
		// slett vedlegg og soknad
		if (dokumentSoknadDto.status != SoknadsStatus.Opprettet)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad ${dokumentSoknadDto.innsendingsId} kan ikke slettes da den er innsendt eller slettet")

		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { slettVedleggOgDensFiler(it) }
		//fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		slettSoknad(dokumentSoknadDto)

		val slettetSoknad = lagDokumentSoknadDto(mapTilSoknadDb(dokumentSoknadDto, dokumentSoknadDto.innsendingsId!!, SoknadsStatus.SlettetAvBruker)
			, dokumentSoknadDto.vedleggsListe.map { mapTilVedleggDb(it, dokumentSoknadDto.id!!)})
		publiserBrukernotifikasjon(slettetSoknad)
	}

	private fun slettVedleggOgDensFiler(vedleggDto: VedleggDto) {
		slettFilerForVedlegg(vedleggDto.id!!)
		slettVedlegg(vedleggDto.id!!)
	}

	// Slett opprettet soknad gitt innsendingsId
	@Transactional
	fun slettSoknadAutomatisk(innsendingsId: String) {
		// Ved automatisk sletting beholdes innslag i basen, men eventuelt opplastede filer slettes
		val dokumentSoknadDto = hentSoknad(innsendingsId)

		if (dokumentSoknadDto.status != SoknadsStatus.Opprettet)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad ${dokumentSoknadDto.innsendingsId} kan ikke slettes da den allerede er innsendt")

		//fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { slettFilerForVedlegg(it.id!!) }
		val slettetSoknadDb = lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.AutomatiskSlettet))

		val slettetSoknadDto = lagDokumentSoknadDto(slettetSoknadDb
			, dokumentSoknadDto.vedleggsListe.map { mapTilVedleggDb(it, dokumentSoknadDto.id!!)})
		publiserBrukernotifikasjon(slettetSoknadDto)
	}

	@Transactional
	fun lagreVedlegg(vedleggDto: VedleggDto, innsendingsId: String): VedleggDto {

		val soknadDto = hentSoknad(innsendingsId)
		if (soknadDto.status != SoknadsStatus.Opprettet)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad $innsendingsId kan ikke endres da den er innsendt eller slettet")

		// Lagre vedlegget i databasen
		val vedleggDbData = lagreVedlegg(mapTilVedleggDb(vedleggDto, soknadDto.id!!))

		// Oppdater soknadens sist endret dato
		oppdaterEndretDato(soknadDto.id)

		val vedleggDtoSaved = lagVedleggDto(vedleggDbData, vedleggDto.document)

		return vedleggDtoSaved
	}

	@Transactional
	fun slettVedlegg(soknadDto: DokumentSoknadDto, vedleggsId: Long) {
		if (soknadDto.status != SoknadsStatus.Opprettet)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad ${soknadDto.innsendingsId} kan ikke endres da den allerede er innsendt")

		val vedleggDto = soknadDto.vedleggsListe.filter { it.id == vedleggsId }.firstOrNull()
		if ( vedleggDto == null )
			throw ResourceNotFoundException(null, "Angitt vedlegg $vedleggsId eksisterer ikke for søknad ${soknadDto.innsendingsId}")

		if (vedleggDto.erHoveddokument)
			throw IllegalActionException("Søknaden må alltid ha sitt hovedskjema", "Kan ikke slette hovedskjema på en søknad")
		if (!vedleggDto.vedleggsnr.equals("N6"))
			throw IllegalActionException("Vedlegg som er obligatorisk for søknaden kan ikke slettes av søker", "Kan ikke slette påkrevd vedlegg")

		slettVedleggOgDensFiler(vedleggDto, soknadDto.id!!)

	}

	private fun slettVedleggOgDensFiler(vedleggDto: VedleggDto, soknadsId: Long) {
		// Ikke slette hovedskjema, og ikke obligatoriske. Slette vedlegget og dens opplastede filer
			slettVedleggOgDensFiler(vedleggDto)
			// Oppdatere soknad.sisteendret
			oppdaterEndretDato(soknadsId)
	}

	@Transactional
	fun sendInnSoknad(soknadDtoInput: DokumentSoknadDto) {

		// Det er ikke nødvendig å opprette og lagre kvittering(L7) i følge diskusjon 3/11.

		// anta at filene til et vedlegg allerede er konvertert til PDF ved lagring, men må merges og sendes til soknadsfillager
		// dersom det ikke er lastet opp filer på et obligatoris vedlegg, skal status settes SENDES_SENERE
		// etter at vedleggsfilen er overført soknadsfillager, skal lokalt lagrede filer på vedlegget slettes.

		var soknadDto = soknadDtoInput
		if (soknadDto.ettersendingsId != null) {
			// Hvis ettersending, så må det genereres et dummy hoveddokument
			val hovedDokumentDto = soknadDto.vedleggsListe.filter { it.erHoveddokument && !it.erVariant }.first()
			val dummySkjema = try {
				PdfGenerator().lagForsideEttersending(soknadDto)
			} catch (ex: Exception) {
				throw BackendErrorException(ex.message, "Feil ved generering av skjema for ettersending")
			}
			lagreFil(soknadDto, FilDto(null, hovedDokumentDto.id!!, hovedDokumentDto.vedleggsnr!!, "application/pdf", dummySkjema, LocalDateTime.now() ))
			soknadDto = hentSoknad(soknadDto.innsendingsId!!)
		}

		// Vedleggsliste med opplastede dokument og status= LASTET_OPP for de som skal sendes soknadsfillager
		val alleVedlegg: List<VedleggDto> = ferdigstillVedlegg(soknadDto)
		val opplastedeVedlegg = alleVedlegg.filter { it.opplastingsStatus == OpplastingsStatus.LASTET_OPP }.toList()

		if (opplastedeVedlegg.isNullOrEmpty() || (soknadDto.ettersendingsId != null && opplastedeVedlegg.size == 1 )) {
			throw IllegalActionException("Søker må ha lastet opp dokumenter til søknaden for at den skal kunne sendes inn", "Innsending avbrutt da ingen opplastede filer å sende inn")
		}

		try {
			fillagerAPI.lagreFiler(soknadDto.innsendingsId!!, opplastedeVedlegg)
		} catch (ex: Exception) {
			throw BackendErrorException(ex.message, "Feil ved sending av filer for søknad ${soknadDto.innsendingsId} til NAV")
		}

		// send soknadmetada til soknadsmottaker
		try {
			soknadsmottakerAPI.sendInnSoknad(soknadDto)
		} catch (ex: Exception) {
			throw BackendErrorException(ex.message, "Feil ved sending av søknad ${soknadDto.innsendingsId} til NAV")
		}

		// Slett opplastede vedlegg som er sendt til soknadsfillager.
		opplastedeVedlegg.forEach { slettFilerForVedlegg(it.id!!) }

		// oppdater databasen med status og innsendingsdato
		opplastedeVedlegg. forEach { vedleggRepository.save(mapTilVedleggDb(it, soknadDto.id!!, OpplastingsStatus.INNSENDT)) }
		alleVedlegg.filter { it.opplastingsStatus == OpplastingsStatus.IKKE_VALGT }. forEach { oppdaterVedlegg(soknadDto.innsendingsId!!, mapTilVedleggDb(it, soknadDto.id!!, OpplastingsStatus.SEND_SENERE)) }

		try {
			vedleggRepository.flush()
		} catch (ex: Exception) {
			throw BackendErrorException(ex.message, "Feil ved sending av søknad ${soknadDto.innsendingsId} til NAV")
		}

		try {
			soknadRepository.saveAndFlush(mapTilSoknadDb(soknadDto, soknadDto.innsendingsId!!, SoknadsStatus.Innsendt ))
		} catch (ex: Exception) {
			throw BackendErrorException(ex.message, "Feil ved sending av søknad ${soknadDto.innsendingsId} til NAV")
		}
		// send brukernotifikasjon
		publiserBrukernotifikasjon(hentSoknad(soknadDto.innsendingsId!!))
	}


	// For alle vedlegg til søknaden:
	// Hoveddokument kan ha ulike varianter. Hver enkelt av disse sendes som ulike filer til soknadsfillager.
	// Bruker kan ha lastet opp flere filer for øvrige vedlegg. Disse må merges og sendes som en fil.
	private fun ferdigstillVedlegg(soknadDto: DokumentSoknadDto): List<VedleggDto> {
		var vedleggDtos = mutableListOf<VedleggDto>()

		// Listen av varianter av hoveddokumenter, hent lagrede filer og opprett
		soknadDto.vedleggsListe.filter{ it.erHoveddokument }.forEach {
			vedleggDtos.add(lagVedleggDtoMedOpplastetFil(hentOgMergeVedleggsFiler(soknadDto, soknadDto.innsendingsId!!, it), it) ) }
		// For hvert øvrige vedlegg merge filer og legg til
		soknadDto.vedleggsListe.filter { !it.erHoveddokument}.forEach {
			val filDto = hentOgMergeVedleggsFiler(soknadDto, soknadDto.innsendingsId!!, it)
			if (filDto != null) vedleggDtos.add(lagVedleggDtoMedOpplastetFil(filDto, it)) }
		return vedleggDtos
	}

	private fun hentOgMergeVedleggsFiler(soknadDto: DokumentSoknadDto, innsendingsId: String, vedleggDto: VedleggDto): FilDto? {
		val filer = hentFiler(soknadDto, innsendingsId, vedleggDto.id!!, true, false).filter { it.data != null }
		if (filer.isNullOrEmpty()) return null

		val vedleggsFil: ByteArray? =
			if (vedleggDto.erHoveddokument) {
				if (filer.size > 1) {
					logger.warn("Vedlegg ${vedleggDto.id}: ${vedleggDto.tittel} har flere opplastede filer, velger første")
				}
				filer.get(0).data
			} else {
				PdfMerger().mergePdfer(filer.map { it.data!! })
			}

		return FilDto(null, vedleggDto.id, vedleggDto.vedleggsnr!!, filer.get(0).mimetype, vedleggsFil, filer.get(0).opprettetdato)
	}

	private fun lagVedleggDtoMedOpplastetFil(filDto: FilDto?, vedleggDto: VedleggDto) =
		VedleggDto(vedleggDto.id!!, vedleggDto.vedleggsnr, vedleggDto.tittel,
			vedleggDto.uuid, filDto?.mimetype ?: vedleggDto.mimetype, filDto?.data, vedleggDto.erHoveddokument,
			vedleggDto.erVariant, vedleggDto.erPdfa, vedleggDto.skjemaurl,
			if (filDto?.data != null) OpplastingsStatus.LASTET_OPP else vedleggDto.opplastingsStatus
			, filDto?.opprettetdato ?: vedleggDto.opprettetdato)



	private  fun lagFilDto(filDbData: FilDbData, medFil: Boolean = true) = FilDto(filDbData.id, filDbData.vedleggsid
									, filDbData.filnavn, filDbData.mimetype, if (medFil) filDbData.data else null, filDbData.opprettetdato)

	private fun lagVedleggDto(vedleggDbData: VedleggDbData, document: ByteArray? = null) =
		VedleggDto(vedleggDbData.id!!, vedleggDbData.vedleggsnr, vedleggDbData.tittel,
			vedleggDbData.uuid, vedleggDbData.mimetype, document, vedleggDbData.erhoveddokument,
			vedleggDbData.ervariant, vedleggDbData.erpdfa, vedleggDbData.vedleggsurl, vedleggDbData.status, vedleggDbData.opprettetdato)

	private fun lagDokumentSoknadDto(soknadDbData: SoknadDbData, vedleggDbDataListe: List<VedleggDbData>) =
		DokumentSoknadDto(soknadDbData.id!!, soknadDbData.innsendingsid, soknadDbData.ettersendingsid,
			soknadDbData.brukerid, soknadDbData.skjemanr, soknadDbData.tittel, soknadDbData.tema, soknadDbData.spraak,
			soknadDbData.status, soknadDbData.opprettetdato, soknadDbData.endretdato, soknadDbData.innsendtdato
			, vedleggDbDataListe.map { lagVedleggDto(it) })

	private fun mapTilFilDb(filDto: FilDto) = FilDbData(filDto.id, filDto.vedleggsid, filDto.filnavn
							, filDto.mimetype, filDto.data, filDto.opprettetdato ?: LocalDateTime.now())

	private fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long) =
		mapTilVedleggDb(vedleggDto, soknadsId, vedleggDto.skjemaurl, vedleggDto.opplastingsStatus)

	private fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long, opplastingsStatus: OpplastingsStatus) =
		mapTilVedleggDb(vedleggDto, soknadsId, vedleggDto.skjemaurl, opplastingsStatus)

	private fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long, kodeverkSkjema: KodeverkSkjema?) =
		mapTilVedleggDb(vedleggDto, soknadsId,  if (kodeverkSkjema != null ) kodeverkSkjema.url else vedleggDto.skjemaurl, vedleggDto.opplastingsStatus)

	private fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long, url: String?, opplastingsStatus: OpplastingsStatus) =
		VedleggDbData(vedleggDto.id, soknadsId, opplastingsStatus
			, vedleggDto.erHoveddokument, vedleggDto.erVariant, vedleggDto.erPdfa, vedleggDto.vedleggsnr, vedleggDto.tittel
			, vedleggDto.mimetype, vedleggDto.uuid, vedleggDto.opprettetdato, LocalDateTime.now()
			, if (url != null) url else vedleggDto.skjemaurl)


	private fun mapTilSoknadDb(dokumentSoknadDto: DokumentSoknadDto, innsendingsId: String, status: SoknadsStatus? = SoknadsStatus.Opprettet) =
		SoknadDbData(dokumentSoknadDto.id, innsendingsId,
			dokumentSoknadDto.tittel, dokumentSoknadDto.skjemanr, dokumentSoknadDto.tema, dokumentSoknadDto.spraak,
			status ?: dokumentSoknadDto.status, dokumentSoknadDto.brukerId, dokumentSoknadDto.ettersendingsId,
			dokumentSoknadDto.opprettetDato, LocalDateTime.now(), if (status == SoknadsStatus.Innsendt) LocalDateTime.now() else dokumentSoknadDto.innsendtDato)
}

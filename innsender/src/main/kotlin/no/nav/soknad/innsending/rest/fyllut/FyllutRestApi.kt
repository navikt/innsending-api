package no.nav.soknad.innsending.rest.fyllut

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.FyllutApi
import no.nav.soknad.innsending.brukernotifikasjon.NotificationOptions
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.location.UrlHandler
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.ArenaService
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.NotificationService
import no.nav.soknad.innsending.service.PrefillService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH
import no.nav.soknad.innsending.util.Constants.CLAIM_ACR_LEVEL_4
import no.nav.soknad.innsending.util.Constants.TOKENX
import no.nav.soknad.innsending.util.logging.CombinedLogger
import no.nav.soknad.innsending.util.mapping.SkjemaDokumentSoknadTransformer
import no.nav.soknad.innsending.util.mapping.mapTilSkjemaDto
import no.nav.soknad.innsending.util.models.attachmentdto.sanitize
import no.nav.soknad.innsending.util.models.erEttersending
import no.nav.soknad.innsending.util.models.hoveddokument
import no.nav.soknad.innsending.util.models.kanGjoreEndringer
import no.nav.soknad.innsending.util.validators.validerBrukerOgAvsender
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@ProtectedWithClaims(issuer = TOKENX, claimMap = [CLAIM_ACR_LEVEL_4, CLAIM_ACR_IDPORTEN_LOA_HIGH], combineWithOr = true)
class FyllutRestApi(
	private val urlHandler: UrlHandler,
	private val soknadService: SoknadService,
	private val tilgangskontroll: Tilgangskontroll,
	private val prefillService: PrefillService,
	private val subjectHandler: SubjectHandlerInterface,
	private val arenaService: ArenaService,
	private val notificationService: NotificationService,
	private val innsendingService: InnsendingService,
) : FyllutApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val combinedLogger = CombinedLogger(logger)

	@Timed(InnsenderOperation.OPPRETT)
	override fun fyllUtOpprettSoknad(
		skjemaDto: SkjemaDto,
		force: Boolean?,
		envQualifier: EnvQualifier?,
	): ResponseEntity<Any> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		val applikasjon = subjectHandler.getClientId()

		combinedLogger.log(
			"Skal opprette søknad fra FyllUt: ${skjemaDto.skjemanr}, ${skjemaDto.tittel}, ${skjemaDto.tema}, ${skjemaDto.spraak}",
			brukerId
		)

		val redirectVedPaabegyntSoknad =
			redirectVedPaabegyntSoknad(brukerId, skjemaDto, force == true)
		if (redirectVedPaabegyntSoknad != null) return redirectVedPaabegyntSoknad

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			input = SkjemaDokumentSoknadTransformer().konverterSkjemaDtoTilV2(skjemaDto),
			existingSoknad = null,
			brukerId = brukerId,
			applikasjon = applikasjon
		)
		val opprettetSoknad = soknadService.opprettNySoknad(dokumentSoknadDto)

		combinedLogger.log(
			"${opprettetSoknad.innsendingsId}: Soknad fra FyllUt opprettet",
			brukerId
		)
		notificationService.create(opprettetSoknad.innsendingsId!!, NotificationOptions(envQualifier = envQualifier))

		return ResponseEntity.status(HttpStatus.CREATED).body(opprettetSoknad)
	}

	// Skal redirecte til påbegynt søknad hvis bruker har en søknad under arbeid (med mindre det er sendt inn eksplisitt paremeter for å opprette ny søknad likevel)
	private fun redirectVedPaabegyntSoknad(
		brukerId: String,
		skjemaDto: SkjemaDto,
		forceCreate: Boolean = false
	): ResponseEntity<Any>? {
		val aktiveSoknader = soknadService.hentAktiveSoknader(brukerId, skjemaDto.skjemanr)
		val harSoknadUnderArbeid = aktiveSoknader.isNotEmpty()

		if (harSoknadUnderArbeid && !forceCreate) {
			logger.info("Redirecter til side for å velge mellom å fortsette påbegynt søknad eller opprette ny søknad.")
			val body = BodyStatusResponseDto(
				status = ErrorCode.SOKNAD_ALREADY_EXISTS.code,
				info = "Søknad for dette skjemanummeret er allerede påbegynt. Redirect til side for å velge mellom å fortsette påbegynt søknad eller opprette ny søknad.",
			)
			return ResponseEntity.status(HttpStatus.OK).body(body)
		}
		return null
	}

	@Timed(InnsenderOperation.ENDRE)
	override fun fyllUtOppdaterSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<SkjemaDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		val applikasjon = subjectHandler.getClientId()

		combinedLogger.log("$innsendingsId: Skal oppdatere søknad fra FyllUt", brukerId)

		logger.info("fyllUtOppdaterSoknad: Hoveddokument er lastet opp ${skjemaDto.hoveddokument.document != null}")

		val existingSoknad = soknadService.hentSoknad(innsendingsId)
		validerSoknadsTilgang(existingSoknad)

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			input = SkjemaDokumentSoknadTransformer().konverterSkjemaDtoTilV2(skjemaDto),
			existingSoknad = existingSoknad,
			brukerId = brukerId,
			applikasjon = applikasjon
		)

		logger.info("fyllUtOppdaterSoknad: Skal oppdatere søknad med hoveddokument er lastet opp ${dokumentSoknadDto.hoveddokument?.document != null}")
		val updatedSoknad = soknadService.updateSoknad(innsendingsId, dokumentSoknadDto)

		combinedLogger.log("$innsendingsId: Soknad fra FyllUt oppdatert", brukerId)

		return ResponseEntity.status(HttpStatus.OK).body(updatedSoknad)
	}

	@Timed(InnsenderOperation.ENDRE)
	override fun fyllUtUtfyltSoknad(
		innsendingsId: String,
		skjemaDto: SkjemaDto,
		envQualifier: EnvQualifier?
	): ResponseEntity<Unit> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		val applikasjon = subjectHandler.getClientId()

		combinedLogger.log("$innsendingsId: Skal fullføre søknad fra FyllUt", brukerId)
		logger.info("fyllUtUtfyltSoknad: Hoveddokument er lastet opp ${skjemaDto.hoveddokument.document != null}")

		val existingSoknad = soknadService.hentSoknad(innsendingsId)
		validerSoknadsTilgang(existingSoknad)

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			input = SkjemaDokumentSoknadTransformer().konverterSkjemaDtoTilV2(skjemaDto),
			existingSoknad = existingSoknad,
			brukerId = brukerId,
			applikasjon = applikasjon
		)

		logger.info("fyllUtUtfyltSoknad: Skal oppdatere søknad med hoveddokument er lastet opp = ${dokumentSoknadDto.hoveddokument?.document != null}, status = ${dokumentSoknadDto.hoveddokument?.opplastingsStatus}")
		soknadService.updateUtfyltSoknad(innsendingsId, dokumentSoknadDto)

		combinedLogger.log("$innsendingsId: Utfylt søknad fra Fyllut", brukerId)

		return ResponseEntity
			.status(HttpStatus.FOUND)
			.location(URI.create(urlHandler.getSendInnUrl(envQualifier) + "/" + innsendingsId))
			.build()
	}

	@Timed(InnsenderOperation.HENT)
	override fun fyllUtHentSoknad(innsendingsId: String): ResponseEntity<SkjemaDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		combinedLogger.log("$innsendingsId: Kall fra FyllUt for å hente søknad", brukerId)

		val dokumentSoknadDto = soknadService.hentSoknadMedHoveddokumentVariant(innsendingsId)
		validerSoknadsTilgang(dokumentSoknadDto)

		combinedLogger.log("$innsendingsId: Hentet søknad fra FyllUt", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(mapTilSkjemaDto(dokumentSoknadDto))
	}

	@Timed(InnsenderOperation.SLETT)
	override fun fyllUtSlettSoknad(innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		combinedLogger.log("$innsendingsId: Kall fra FyllUt for å slette søknad", brukerId)

		val dokumentSoknadDto = soknadService.hentSoknad(innsendingsId)
		validerSoknadsTilgang(dokumentSoknadDto)

		notificationService.close(innsendingsId)
		soknadService.slettSoknadAvBruker(dokumentSoknadDto)
		combinedLogger.log("$innsendingsId: Slettet søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet soknad med id $innsendingsId"))

	}

	override fun fyllUtPrefillData(properties: List<String>): ResponseEntity<PrefillData> {
		val userId = tilgangskontroll.hentBrukerFraToken()
		combinedLogger.log("Kall fra FyllUt for å hente prefill-data for $properties", userId)

		val prefillData = prefillService.getPrefillData(properties, userId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(prefillData)
	}

	// Used by fyllut and fyllut-ettersending
	@Timed(InnsenderOperation.HENT)
	override fun hentSoknaderForSkjemanr(
		skjemanr: String,
		soknadstyper: List<SoknadType>?
	): ResponseEntity<List<DokumentSoknadDto>> {
		val brukerIds = tilgangskontroll.hentPersonIdents()

		brukerIds.forEach {
			combinedLogger.log(
				"Kall for å hente søknader med $skjemanr for bruker. Soknadstyper=${soknadstyper ?: "ikke spesifisert"}",
				it
			)
		}

		val typeFilter = soknadstyper?.toTypedArray() ?: emptyArray()
		brukerIds.forEach {
			combinedLogger.log(
				"Henter søknader med søknadstyper=${soknadstyper ?: "<alle>"} for $skjemanr",
				it
			)
		}
		val soknader = brukerIds.flatMap { soknadService.hentAktiveSoknader(it, skjemanr, *typeFilter) }

		return ResponseEntity.status(HttpStatus.OK).body(soknader)
	}

	override fun fyllUtAktiviteter(dagligreise: Boolean): ResponseEntity<List<Aktivitet>> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		combinedLogger.log("Kall fra FyllUt for å hente aktiviteter med type: $dagligreise", brukerId)

		val aktivitetEndepunkt = if (dagligreise) AktivitetEndepunkt.dagligreise else AktivitetEndepunkt.aktivitet
		val aktiviteter = arenaService.getAktiviteterWithMaalgrupper(aktivitetEndepunkt)
		return ResponseEntity.status(HttpStatus.OK).body(aktiviteter)
	}

	@Timed(InnsenderOperation.SEND_INN)
	override fun submitDigitalApplication(
		innsendingsId: UUID,
		submitApplicationRequest: SubmitApplicationRequest,
		navEnvQualifier: EnvQualifier?
	): ResponseEntity<ApplicationSubmissionResponse> {
		submitApplicationRequest.validerBrukerOgAvsender()
		val innsendingsIdStr = innsendingsId.toString()
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		val affectedUser = if (submitApplicationRequest.bruker != null && brukerId != submitApplicationRequest.bruker) {
			BrukerDto(id = submitApplicationRequest.bruker!!, idType = BrukerDto.IdType.FNR )
		} else null

		combinedLogger.log("$innsendingsIdStr: Kall fra FyllUt for sende inn søknad", "loggedIn: $brukerId / affected: ${affectedUser?.id ?: brukerId}")

		val soknad = soknadService.hentSoknad(innsendingsIdStr)
		validerSoknadsTilgang(soknad)

		if (soknad.erEttersending) {
			// Denne funksjonen støtter ikke innsending av ettersendinger fra FyllUt pga logikk rundt dummy-hoveddokument
			// og at det må sjekkes om ettersendingssøknaden har endringer som kan sendes inn.
			// Derfor må ettersendingssøknader fremdeles sendes inn fra SendInnFrontend.
			return ResponseEntity(HttpStatus.NOT_IMPLEMENTED)
		}

		val (submissionSummary, ettersendingsId) = innsendingService.preSubmitApplication(
			soknad,
			submitApplicationRequest.mainDocument,
			submitApplicationRequest.mainDocumentAlt,
			submitApplicationRequest.attachments.sanitize(),
			submitApplicationRequest.avsender, affectedUser
		)
		innsendingService.sendInnForArkivering(innsendingsIdStr)

		notificationService.close(innsendingsIdStr)
		if (ettersendingsId != null) {
			notificationService.create(
				ettersendingsId.toString(),
				NotificationOptions(erSystemGenerert = true, envQualifier = navEnvQualifier)
			)
		}

		return ResponseEntity.ok(submissionSummary)
	}

	private fun validerSoknadsTilgang(dokumentSoknadDto: DokumentSoknadDto) {
		tilgangskontroll.harTilgang(dokumentSoknadDto)
		if (!dokumentSoknadDto.kanGjoreEndringer) {
			throw IllegalActionException(
				message = "Søknaden kan ikke vises. Søknaden er slettet eller innsendt og kan ikke vises eller endres.",
				errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
			)
		}
	}

}

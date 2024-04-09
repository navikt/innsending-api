package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.*
import no.nav.soknad.innsending.util.models.hoveddokumentVariant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime


@Service
class TilleggsstonadService(
	private val repo: RepositoryUtils,
	private val soknadService: SoknadService,
	private val filService: FilService,
	private val vedleggService: VedleggService,
	) {
	private val logger = LoggerFactory.getLogger(javaClass)

	private val tilleggsstonadSkjema =
		listOf(
			"NAV 11-12.15B", // Støtte til Barnepass
			"NAV 11-12.16B", // Støtte til Læremidler
			"NAV 11-12.17B", // Støtte til samling
			"NAV 11-12.18B", // Støtte til ved oppstart, avslutning eller hjemreiser
			"NAV 11-12.19B", // Støtte til bolig og overnatting
			"NAV 11-12.21B", // Støtte til daglig reise
			"NAV 11-12.22B", // Støtte til reise for å komme i arbeid
			"NAV 11-12.23B", // Støtte til flytting
			"NAV 11-12.24B",
			"kjoreliste" // TODO fjern
		)


	fun isTilleggsstonad(soknadDto: DokumentSoknadDto): Boolean {
		if ("TSO".equals(soknadDto.tema, true) || "TSR".equals(soknadDto.tema, true)) {
			logger.debug("${soknadDto.innsendingsId}: Skal sjekke om generering av XML for Tilleggstonad med skjemanummer ${soknadDto.skjemanr}")
			return tilleggsstonadSkjema.contains(soknadDto.skjemanr)
		}
		return false
	}

	fun addXmlDokumentvariantToSoknad(soknadDto: DokumentSoknadDto): DokumentSoknadDto {
		try {
			val jsonVariant = soknadDto.hoveddokumentVariant
			if (jsonVariant == null) {
				logger.warn("${soknadDto.innsendingsId!!}: Json variant av hoveddokument mangler")
				throw BackendErrorException("${soknadDto.innsendingsId}: json fil av søknaden mangler")
			}

			// Create dokumentDto for xml variant of main document
			val xmlDocumentVariant = vedleggService.lagreNyHoveddokumentVariant(soknadDto, Mimetype.applicationSlashXml)
			logger.info("${soknadDto.innsendingsId}: Lagt til xmlVedlegg på vedleggsId = ${xmlDocumentVariant.id}")

			val jsonFil = filService.hentFiler(
				soknadDto = soknadDto,
				innsendingsId = soknadDto.innsendingsId!!,
				vedleggsId = jsonVariant.id!!,
				medFil = true
			).first().data

			val jsonObj: JsonApplication<*>
			val xmlFile: ByteArray
			if (soknadDto.skjemanr == "NAV 11-12.24B" || soknadDto.skjemanr == "kjoreliste") { // TODO fjern kjoreliste
				jsonObj = convertToJsonDrivingListJson(soknadDto = soknadDto, jsonFil)
				xmlFile = json2Xml(jsonObj, soknadDto)
			} else {
				jsonObj = convertToJsonTilleggsstonad(soknadDto = soknadDto, jsonFil)
				xmlFile = json2Xml(soknadDto = soknadDto, tilleggstonadJsonObj = jsonObj)
			}

			// Persist created xml file
			filService.lagreFil(
				soknadService.hentSoknad(soknadDto.innsendingsId!!),
				FilDto(
					vedleggsid = xmlDocumentVariant.id!!,
					filnavn = soknadDto.skjemanr + ".xml",
					mimetype = Mimetype.applicationSlashXml,
					storrelse = xmlFile.size,
					data = xmlFile,
					opprettetdato = OffsetDateTime.now()
				)
			)
			// Update state of json variant
			vedleggService.endreVedleggStatus(
				soknadDto,
				jsonVariant.id!!,
				OpplastingsStatusDto.sendesIkke
			)

			// Based on skjemanumber and maalgruppe, it might be neccessary to change the application's tema from TSO to TSR
			if (jsonObj.applicationDetails is JsonTilleggsstonad)
				sjekkOgOppdaterTema(soknadDto, jsonObj.applicationDetails.maalgruppeinformasjon)
			else if (jsonObj.applicationDetails is JsonDrivingListSubmission)
				sjekkOgOppdaterTema(soknadDto, jsonObj.applicationDetails.maalgruppeinformasjon)

			return soknadService.hentSoknad(soknadDto.innsendingsId!!)
		} catch (ex: Exception) {
			throw BackendErrorException("${soknadDto.innsendingsId}: Konvertering av JSON til XML feilet", ex)
		}
	}

	private fun sjekkOgOppdaterTema(soknadDto: DokumentSoknadDto, maalgruppeInformasjon: JsonMaalgruppeinformasjon?) {
		val relevanteSkjemaNrForTsr = listOf(
			"NAV 11-12.18B", // Støtte til ved oppstart, avslutning eller hjemreiser
			"NAV 11-12.21B", // Støtte til daglig reise
			"NAV 11-12.22B", // Støtte til reise for å komme i arbeid
			"NAV 11-12.24B",
			"kjoreliste" // TODO fjern kjoreliste
		)
		val relevanteMaalgrupperForTsr = listOf(
			MaalgruppeType.ARBSOKERE.name,
			MaalgruppeType.MOTDAGPEN.name,
			MaalgruppeType.MOTTILTPEN.name,
			MaalgruppeType.ANNET.name
		)
		if (!relevanteSkjemaNrForTsr.contains(soknadDto.skjemanr)) return

		if (!relevanteMaalgrupperForTsr.contains(maalgruppeInformasjon?.maalgruppetype)) return

		repo.endreTema(soknadDto.id!!, soknadDto.innsendingsId!!, "TSR")
	}

}

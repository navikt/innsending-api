package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.DokumentSoknadDto

fun convertToJsonDrivingListJson(
	soknadDto: DokumentSoknadDto,
	jsonFile: ByteArray?
): JsonApplication<JsonDrivingListSubmission> {
	if (jsonFile == null || jsonFile.isEmpty())
		throw BackendErrorException("${soknadDto.innsendingsId}: json fil av søknaden mangler")

	val mapper = jacksonObjectMapper()
	mapper.findAndRegisterModules()
	val json = mapper.readValue(jsonFile, Root::class.java)

	return JsonApplication(
		timezone = json.data.metadata?.timezone,
		language = json.language,
		applicationDetails = convertToJsonDrivingListSubmission(json.data.data, soknadDto)
	)
}


fun convertToJsonDrivingListSubmission(
	drivingListSubmission: Application,
	soknadDto: DokumentSoknadDto
): JsonDrivingListSubmission {
	if (drivingListSubmission.drivinglist == null)
		throw BackendErrorException("${soknadDto.innsendingsId}: Søknaden mangler vedtaksId")

	return JsonDrivingListSubmission(
		tilleggsopplysninger = drivingListSubmission.tilleggsopplysninger,
		maalgruppeinformasjon = convertToJsonMaalgruppeinformasjon(
			drivingListSubmission.aktiviteterOgMaalgruppe,
			flervalg = null,
			regArbSoker = null
		),
		expensePeriodes = JsonDrivingListExpences(
			selectedVedtaksId = drivingListSubmission.drivinglist.selectedVedtaksId,
			tema = drivingListSubmission.drivinglist.tema,
			dates = drivingListSubmission.drivinglist.dates.map {
				JsonDailyExpences(
					date = it.date,
					parking = if (it.parking != null && it.parking.isNotEmpty()) it.parking.toDouble() else null,
					betalingsplanId = it.betalingsplanId
				)
			}
		)
	)
}
